# infra/aws — k3s 클러스터 (Terraform)

NHN NKS → AWS 이관용. 설계 배경은 `docs/infra/nhn-to-aws-k3s-migration.md`.

## 구성

```
가비아 DNS  dev-api.moamap.co.kr  A → EIP
   └→ worker-1:443  traefik (TLS 종료, Let's Encrypt) → gateway-service → user/map/place

public  10.0.1.0/24 · 10.0.2.0/24
   master(t4g.small, taint) + worker ×2(t4g.medium)  ← 공인 IP, IGW로 직접 아웃바운드
   NAT 없음. S3는 게이트웨이 엔드포인트(무료)
private 10.0.11.0/24 · 10.0.12.0/24
   RDS postgres만. 인터넷 경로 없음(로컬 라우트만)
```

| 노드 | 타입 | 역할 |
|---|---|---|
| master ×1 | t4g.small | k3s server (embedded etcd, `--cluster-init`). `node-role.kubernetes.io/control-plane:NoSchedule` taint |
| worker ×2 | t4g.medium | k3s agent. 앱·Redis·RabbitMQ (Postgres는 RDS) |

**SG 3개**
- `node` — 전 노드. 인바운드는 자기참조뿐 → **마스터는 인터넷에서 열린 포트가 없다**
- `ingress` — 워커에만 추가. 80/443 공개 (80은 Let's Encrypt HTTP-01 챌린지에도 필요)
- `rds` — 노드 SG에서만 5432

22는 열지 않는다. 접속은 SSM Session Manager.

## 실행

```bash
cp terraform.tfvars.example terraform.tfvars   # 값 채우기 (커밋 금지)
terraform init && terraform apply
```

state 원격화는 `versions.tf`의 backend 주석 참고. **state에 k3s 조인 토큰과 DB 비밀번호가 들어가므로 버킷은 비공개·암호화.**

## apply 이후 (Terraform 밖)

```bash
MASTER=$(terraform output -raw master_instance_id)
aws ssm start-session --target $MASTER    # /etc/rancher/k3s/k3s.yaml 복사
aws ssm start-session --target $MASTER --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["6443"],"localPortNumber":["6443"]}'
kubectl get nodes    # master 1(taint) + worker 2
```

1. 가비아 A 레코드 → `terraform output -raw ingress_eip`
2. traefik 내장 ACME 설정 (`k8s/cluster/traefik-config.yaml`) — cert-manager는 쓰지 않는다

   ```yaml
   apiVersion: helm.cattle.io/v1
   kind: HelmChartConfig
   metadata: { name: traefik, namespace: kube-system }
   spec:
     valuesContent: |-
       persistence: { enabled: true, size: 128Mi }   # acme.json 보관
       certResolvers:
         le:
           email: <team-email>
           httpChallenge: { entryPoint: web }
           storage: /data/acme.json
   ```
   Ingress에 `traefik.ingress.kubernetes.io/router.tls.certresolver: le` 를 붙인다.
   발급 실패 시 rate limit(도메인당 주 50회)이 있으니 **스테이징 issuer로 먼저 검증**한다.
3. EBS CSI 드라이버 + `gp3` StorageClass (traefik acme.json·RabbitMQ·Prometheus PVC용)
4. `moamap-secrets`의 `DB_HOST` = `terraform output -raw db_endpoint`, `DB_PASSWORD` = `terraform output -raw db_password`
5. ArgoCD, kube-prometheus-stack
6. CI: `terraform output ci_role_arn`을 `aws-actions/configure-aws-credentials`에, 이미지 경로는 `ecr_repository_urls`

## 주의

- **arm64(Graviton)**. CI에서 `--platform linux/arm64`로 빌드해야 파드가 뜬다.
- 노드는 `azs[0]` 한 AZ에만 (EBS가 AZ에 묶임). RDS 서브넷 그룹만 2AZ.
- **traefik `replicas: 1` 유지.** acme.json을 여러 파드가 공유할 수 없다.
- **worker-1이 죽으면 EIP 수동 이동** — `aws_eip_association`의 `instance_id`를 바꾸고 apply. 자동 페일오버는 없다(ALB 미사용).
- 계정에 GitHub OIDC provider가 이미 있으면 `create_github_oidc_provider = false`.
- 실데이터 이전 후 `db_deletion_protection = true`.
