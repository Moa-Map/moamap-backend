# 모아맵 인프라 마이그레이션 설계서 — NHN Cloud NKS → AWS k3s

| | |
|---|---|
| 작성일 | 2026-09-07 |
| 이슈 | #105 |
| 기준 커밋 | `develop` @ `5a2e871` |
| 상태 | **설계 확정, 착수 전** |
| 목적 | 현재 NHN 인프라의 정확한 기록 + AWS k3s 마이그레이션 설계 + 결정 근거. 작업 전에 팀 전원이 읽는다. |

---

## 0. 이 문서를 읽는 법

- **2장**은 "지금 무엇이 어떻게 떠 있는가"다. 실제 클러스터를 `kubectl`로 읽고 매니페스트·Terraform과 대조해 적었다. 추측 없음.
- **3장**은 "어디로 갈 것인가"다. 아직 만들지 않았다.
- **1장**과 **5장**은 결정 근거다. 무엇을 골랐는지보다 **왜 그걸 골랐고 무엇을 포기했는지**를 적었다. 설계를 바꾸려면 여기부터 고친다.
- 가격은 전부 어림값이다. 확정 전 AWS Pricing Calculator로 검증한다.

---

## 1. 배경과 결정

### 1.1 왜 옮기는가

모아맵 백엔드는 NHN Cloud의 관리형 쿠버네티스(NKS)에서 구동 중이다. AWS로 이전해야 하는 상황이 생겼고, 이 기회에 **"관리형에 기대지 않고 쿠버네티스를 직접 운영하는 경험"** 을 얻는 것을 함께 목표로 삼았다.

### 1.2 제약 조건

| 제약 | 내용 |
|---|---|
| 비용 | 대회/포트폴리오 프로젝트. 월 고정비를 최소화해야 한다. **EKS 컨트롤플레인(~$73/월)은 감당 불가** |
| 학습 | 쿠버네티스를 "쓰는" 게 아니라 "운영하는" 경험이 필요하다. **단일 EC2 + Docker Compose는 목적에 안 맞는다** |
| 자산 | kustomize 매니페스트, ArgoCD GitOps, GitHub Actions CI/CD가 이미 있다. **버리지 않는다** |
| 인력 | 백엔드 개발자 팀. DevOps 전담 없음. **운영 부담이 개발 시간을 잡아먹으면 안 된다** |
| 워크로드 | Spring Boot 서비스 4개 + Postgres/Redis/RabbitMQ. 전부 `replicas: 1` |

### 1.3 선택지 비교

| 선택지 | 월 비용(대략) | 기존 자산 | k8s 경험 | 운영 부담 | 판정 |
|---|---|---|---|---|---|
| **EKS** | 컨트롤플레인 $73 + 노드 = **$120~** | 그대로 | 관리형이라 내부는 안 보임 | 최소 | ❌ 비용 |
| **ECS Fargate** | 서비스 $45 + 관리형 DB/MQ $60 = **$100~** | **매니페스트 전부 폐기** | 없음 (k8s 아님) | 최소 | ❌ 자산·목적 |
| **EC2 + kubespray** | 노드 4~5대 필요 = **$120~** | 그대로 | 최대 (컨트롤플레인 내부까지) | **최대** (인증서 만료, 플레이북 업그레이드) | ❌ 부담·비용 |
| **EC2 + k3s** | **$30~90** | 그대로 | 컨트롤플레인 내부 제외 전부 | 낮음 | ✅ |

**ECS Fargate의 함정**: "서버리스라 싸다"는 상태 저장 서비스에서 깨진다. Postgres·Redis·RabbitMQ를 Fargate에 올릴 수 없어 RDS·ElastiCache·AmazonMQ가 필요한데, AmazonMQ 최소 인스턴스만 월 $30 수준이다.

**kubespray를 안 고른 이유**: k8s 경험의 95%는 컨트롤플레인 *위*에 있다(스케줄링·스토리지·Ingress·관측·장애 대응). kubespray가 더 주는 건 "apiserver를 별도 파드로 본다", "kubeadm 인증서를 직접 관리한다" 둘뿐이고, 그 대가로 노드당 ~2GB를 컨트롤플레인이 먹고 인증서 1년 만료 같은 운영 함정이 따라온다. 3~4대 예산에서 kubespray를 하면 컨트롤플레인 3대 + 워커 1대가 되어 워크로드 넣을 데가 없다. 컨트롤플레인 내부가 궁금하면 대회 후 스팟 3대로 하루 실습한다(수천 원).

### 1.4 노드 수 결정

**"마스터 1 + 워커 2"는 함정이다.** k3s에서 server 1대 + agent 2대는 비용은 3배인데 server가 죽으면 API·스케줄링이 전부 멈춘다(SPOF 그대로). k3s server 노드는 기본적으로 워크로드도 받으므로, 3대라면 **server 3대(embedded etcd, quorum 2/3)** 가 맞다. etcd는 홀수여야 하고 3이 HA 최소다. 2는 1보다 나쁘다(split brain).

| 구성 | 컨트롤플레인 | 노드 1대 장애 시 | 월 비용 |
|---|---|---|---|
| server 1 | SQLite, SPOF | 전체 다운 | ~$30 (medium) / ~$61 (large) |
| server 1 + agent 2 | SQLite, **SPOF** | server면 전체 다운 | ~$90 |
| **server 3 (etcd)** | **HA** | 유지 | ~$90 (온디맨드) / ~$27 (스팟) |

**단, 3대가 의미 있으려면 워크로드도 따라와야 한다.** 지금은 전부 `replicas: 1`이고 Postgres도 단일이라, 노드만 3대여도 앱 가용성은 안 오른다. 3대 구성은 EBS CSI(파드가 노드를 옮겨도 볼륨이 따라감) + stateless `replicas: 2` + anti-affinity와 세트다. 이걸 **4장 로드맵의 9번**으로 넣었다.

### 1.5 최종 결정

```
EC2 t4g.medium (2vCPU/4GB, Graviton) × 3
k3s server 3대, embedded etcd HA, 전 노드 워크로드 수용
단일 AZ (EBS가 AZ에 묶이므로)
Elastic IP 1개 → traefik(내장 Ingress)가 80/443 수신
EBS gp3 + EBS CSI 드라이버
S3 (사진 + Terraform state), ghcr.io (이미지), 가비아 DNS (유지)
```

---

## 2. 현재 상태 (As-Is) — NHN Cloud

> 2026-09-07 기준. `kubectl`로 실제 클러스터를 읽고 레포의 매니페스트·Terraform과 대조했다.

### 2.1 구성도

```
가비아 DNS  moamap.co.kr
   └─ dev-api.moamap.co.kr  A → 125.6.39.211   (TTL 600)
                                   │
NHN Cloud KR1 ─────────────────────┼─────────────────────────────
  VPC  moamap-vpc      10.0.0.0/16
   └ 서브넷 moamap-subnet 10.0.1.0/24
      └ 인터넷 게이트웨이 (콘솔 수동 생성, Terraform 밖)
      └ NKS "moamap-nks"  Kubernetes v1.32.3
         ├ worker-node-0  u2.c2m4 (2vCPU/4GB)  Ubuntu 22.04  ← 파드 대부분
         └ worker-node-1  u2.c2m4 (2vCPU/4GB)  Ubuntu 22.04  ← rabbitmq만
                                   │
         namespace: dev            │
         ┌─────────────────────────┴───────────────────────────┐
         │  gateway-service  ◄── Service type=LoadBalancer      │
         │        │              (공인 IP 125.6.39.211:80→8080) │
         │        ├─► user-service   :8081  ClusterIP           │
         │        ├─► map-service    :8083  ClusterIP           │
         │        └─► place-service  :8082  ClusterIP           │
         │                                                      │
         │  postgres   StatefulSet + PVC 10Gi (cinder-block)    │
         │  redis      Deployment  (redis:7-alpine)             │
         │  rabbitmq   Deployment  (rabbitmq:3.13-management)   │
         └──────────────────────────────────────────────────────┘
         namespace: prod    (비어 있음 — ArgoCD 앱만 존재)
         namespace: moamap  (비어 있음 — base 기본값 잔재)
         namespace: argocd  (ArgoCD v3.4.5)

  NCR  9c5fef4c-kr1-registry.container.nhncloud.com/moamap-ncr/<svc>:<git-sha>
  Object Storage (S3 호환)  사진 업로드 버킷 + (설계상) tfstate 버킷
```

### 2.2 IaC 범위 — Terraform이 관리하는 것과 아닌 것

`infra/terraform/` (provider `nhn-cloud/nhncloud ~> 1.0`, OpenStack 기반)

**관리함 (2개)**

| 파일 | 리소스 |
|---|---|
| `network.tf` | VPC, 라우팅테이블, 서브넷, 라우팅테이블-게이트웨이 연결 |
| `nks.tf` | NKS 클러스터 + 기본 노드그룹 (`node_count` 변경은 `ignore_changes`) |

**관리 안 함 (콘솔·kubectl 수동)** — 이쪽이 더 많다

| 리소스 | 생성 방법 | 비고 |
|---|---|---|
| 인터넷 게이트웨이 | 콘솔 | provider 미지원. ID만 변수로 주입 |
| NCR 레지스트리 | 콘솔 | `ncr-cred` imagePullSecret으로 참조 |
| Object Storage 버킷 | 콘솔 | 사진 + tfstate |
| keypair | 콘솔 | 노드 SSH용 |
| `cinder-csi-plugin` | NKS 콘솔 애드온 | 블록스토리지 CSI |
| StorageClass `cinder-block` | `kubectl apply -f k8s/cluster/` | `reclaimPolicy: Retain` |
| ArgoCD | 수동 설치 | v3.4.5 |
| Secret `moamap-secrets` | `kubectl apply -f k8s/secrets/` | 실제 파일은 gitignore |
| LoadBalancer | k8s Service가 자동 생성 | 공인 IP 할당 |

**Terraform state**: `versions.tf`에 S3 호환 백엔드가 **주석 처리**되어 있다. 원격 백엔드가 활성화된 적이 없다면 **로컬 state**로 apply된 것이고, 그 `.tfstate` 파일은 apply한 사람의 노트북에만 있다. **정리(destroy) 전에 소재 확인 필수** (4.4 참고).

### 2.3 네트워크

| 항목 | 값 |
|---|---|
| 리전 / AZ | KR1 / kr-pub-a |
| VPC | `moamap-vpc` 10.0.0.0/16 |
| 서브넷 | `moamap-subnet` 10.0.1.0/24 (노드 IP 10.0.1.84, 10.0.1.22) |
| 파드 CIDR | 10.100.0.0/16 (NKS 기본) |
| 서비스 CIDR | 10.254.0.0/16 (NKS 기본) |
| 외부 진입 | LoadBalancer 1개 → 공인 IP 125.6.39.211 |

### 2.4 컴퓨트

| 항목 | 값 |
|---|---|
| 클러스터 | NKS `moamap-nks`, k8s v1.32.3, containerd 1.7.27 |
| 노드 | 2대, `u2.c2m4` (2vCPU/4GB), 부트볼륨 50GB General HDD |
| 총 자원 | 4 vCPU / 8GB |
| 오토스케일 | 비활성 (`ca_enable=false`) |
| 파드 분포 | **node-0에 gateway·user·map·place·postgres·redis 전부**, node-1에 rabbitmq만 → 사실상 단일 노드 |

### 2.5 워크로드 상세

| 워크로드 | 종류 | 이미지 | requests | limits | 포트 | 노출 |
|---|---|---|---|---|---|---|
| gateway-service | Deployment ×1 | NCR `<sha>` | 100m / 256Mi | – / 512Mi | 8080 | **LoadBalancer :80** |
| user-service | Deployment ×1 | NCR `<sha>` | 100m / 256Mi | – / 512Mi | 8081 | ClusterIP |
| map-service | Deployment ×1 | NCR `<sha>` | 100m / 256Mi | – / 512Mi | 8083 | ClusterIP |
| place-service | Deployment ×1 | NCR `<sha>` | 100m / 256Mi | – / 512Mi | 8082 | ClusterIP |
| postgres | StatefulSet ×1 | `postgres:16` | 100m / 256Mi | – / 512Mi | 5432 | ClusterIP |
| redis | Deployment ×1 | `redis:7-alpine` | (미설정) | (미설정) | 6379 | ClusterIP |
| rabbitmq | Deployment ×1 | `rabbitmq:3.13-management` | (미설정) | (미설정) | 5672, 15672 | ClusterIP |
| notification-service | **미배포** | – | – | – | – | – |

- 모든 앱 파드: `runAsNonRoot`, `readOnlyRootFilesystem`, `capabilities.drop: ALL`, `seccompProfile: RuntimeDefault`
- 헬스체크: `/actuator/health` (readiness 20s/10s, liveness 40s/20s)
- Postgres는 **단일 DB `moamap`에 서비스별 스키마** 분리 (`user_service`, `map_service`, `place_service`, `notification_service`)

### 2.6 스토리지

| 항목 | 값 |
|---|---|
| StorageClass | `cinder-block` (`cinder.csi.openstack.org`), **`reclaimPolicy: Retain`** |
| PVC | `data-postgres-0` 10Gi RWO, 37일 경과 |
| 주의 | Retain이라 **PVC를 지워도 블록스토리지 볼륨은 남아 과금된다.** PV까지 지워야 끝난다 |

### 2.7 트래픽 진입 · DNS

```
클라이언트 → 125.6.39.211:80 (NHN LB)
          → gateway-service:8080 (Spring Cloud Gateway)
             ├ JWT 검증 → X-User-Id 주입 (클라가 보낸 X-User-Id는 제거)
             ├ /api/v1/auth/**, /api/v1/users/** → user-service
             ├ /api/v1/maps/**                    → map-service
             └ /api/v1/places/**                  → place-service
```

- **HTTPS 없음.** http만.
- **Ingress 없음.** 게이트웨이 Service가 직접 LoadBalancer.
- 게이트웨이 무인증 공개 경로: `/api/v1/auth/**`, `GET /api/v1/maps/**`, Swagger UI, api-docs.
- DNS: 가비아 `moamap.co.kr`, `dev-api` A 레코드만 등록 (2026-09-05).
- dev 오버레이 설정에 `http://125.6.39.211`이 하드코딩 (`PUBLIC_GATEWAY_URL`, `KAKAO_REDIRECT_URI`).

### 2.8 컨테이너 이미지 · CI/CD

**이미지**: 모노레포 공통 `Dockerfile`, `--build-arg SERVICE=<모듈>`로 서비스별 빌드. `eclipse-temurin:17-jdk`(build) → `eclipse-temurin:17-jre`(runtime), non-root `appuser(1001)`. 태그는 git 커밋 SHA. **amd64 전용** (buildx 미사용).

**파이프라인**

```
PR → develop         ci.yml      ./gradlew build (테스트 포함)
push → develop       ci.yml + cd-dev.yml
                       cd-dev: docker build/push ×4 (NCR)
                               → k8s/overlays/dev/kustomization.yaml 의 newTag 갱신 커밋 [skip ci]
                       ArgoCD moamap-dev 가 감지 → 수동 sync (자동 동기화 OFF)
push → main          cd-prod.yml (prod 미배포라 실질 미사용)
```

- GitHub Secrets: `NCR_ACCESS_KEY`, `NCR_SECRET_KEY`
- ArgoCD Application: `moamap-dev`(develop, `k8s/overlays/dev`), `moamap-prod`(main, `k8s/overlays/prod`)
- **현재 `moamap-dev`는 OutOfSync** — 떠 있는 이미지 `f240de8`, git은 `575820b`. PR #98, #101이 미배포.

### 2.9 설정 · 시크릿 관리

| 종류 | 위치 | 내용 |
|---|---|---|
| ConfigMap `moamap-config` | `k8s/base/configmap.yaml` + overlay 패치 | 서비스 간 내부 URL, DB/Redis/RabbitMQ 호스트, `PUBLIC_GATEWAY_URL`, `KAKAO_REDIRECT_URI`, `DEV_LOGIN_ENABLED` |
| Secret `moamap-secrets` | `k8s/secrets/app-secrets.yaml` (gitignore) | `JWT_SECRET`, `DB_*`, `KAKAO_*`, `GEMINI_*`, `SEOUL_API_KEY`, `OBJECT_STORAGE_*` (endpoint/bucket/access-key/secret-key), `RABBITMQ_*` |
| imagePullSecret | `ncr-cred` | NCR 인증 |

- 오브젝트 스토리지는 **정적 액세스 키**로 인증 (Secret에 평문). AWS 이전 시 IAM Role로 대체 가능 (3.8).
- 시크릿 예시 파일만 커밋 (`*.example.yaml`), 실제 값은 gitignore.

### 2.10 외부 연동

| 서비스 | 용도 | 과금 |
|---|---|---|
| 카카오 OAuth | 소셜 로그인 | 무료 |
| 카카오 로컬 API | 장소 검색·매칭 | 쿼터 |
| Gemini API | 인스타그램/공유링크 장소 추출 | **호출당 과금** |
| 서울 열린데이터광장 | 유동인구 (5분 주기 배치) | 무료 |
| NHN Object Storage | 장소·댓글·지도 커버 사진 (presigned PUT) | 저장량 |

### 2.11 관측

- Actuator `health`, `info`만 노출. 게이트웨이는 공인 IP에 물려 있어 `prometheus` 엔드포인트 **의도적으로 미노출**.
- 각 서비스 `management.metrics.distribution.percentiles-histogram.http.server.requests=true` 설정됨 (p95 계산 가능).
- **클러스터 내 모니터링 스택 없음.** Prometheus/Grafana는 로컬 부하테스트용(`load-test/compose.loadtest.yml`)에만 존재.
- 로그 수집 없음 (`kubectl logs`만).

### 2.12 과금 항목

| 항목 | 수량 | 비고 |
|---|---|---|
| NKS 워커 노드 `u2.c2m4` | 2대 | 컨트롤플레인은 NHN 무료 |
| 부트볼륨 50GB HDD | 2개 | |
| LoadBalancer | 1개 | |
| 블록스토리지 (PVC) | 10Gi | **Retain — PVC 삭제해도 남음** |
| NCR | 커밋 SHA별 이미지 누적 | 정리 안 하면 계속 증가 |
| Object Storage | 사진 + tfstate | |
| 도메인 `moamap.co.kr` | 연 단위 | 가비아 |

### 2.13 알려진 운영 과제 (마이그레이션과 별개로 존재)

| # | 과제 | 비고 |
|---|---|---|
| 1 | ArgoCD `moamap-dev` OutOfSync — 최신 코드 미배포 | 이전 전 sync 또는 이전 후 최신으로 배포 |
| 2 | 파드가 node-0에 집중 — 2노드의 의미 없음 | 이전 시 anti-affinity로 해결 (4.1 10번) |
| 3 | HTTPS 없음 | 이전 시 cert-manager (4.1 7번) |
| 4 | 모니터링·로그 수집 없음 | 이전 시 kube-prometheus-stack (4.1 9번) |
| 5 | Terraform state 소재 불명 | **정리(4.4) 전 반드시 확인** |
| 6 | 정적 오브젝트 스토리지 키를 Secret에 보관 | 이전 시 IAM Role로 대체 (3.8) |

애플리케이션 레벨 보안 개선 사항은 별도 이슈로 관리한다 (이 문서 범위 아님).

---

## 3. 목표 상태 (To-Be) — AWS + k3s

### 3.1 구성도

```
가비아 DNS  moamap.co.kr  (유지, 비용 0)
   ├─ dev-api.moamap.co.kr   A → Elastic IP
   └─ api.moamap.co.kr       A → Elastic IP   (prod 시)
                                   │
AWS ap-northeast-2 (서울), 단일 AZ ──┼─────────────────────────────
  VPC 10.0.0.0/16
   └ public subnet 10.0.1.0/24 + Internet Gateway   (NAT 없음 — 월 $35 절약)
      Security Group: 80/443 ← 0.0.0.0/0, 22 ← 관리자 IP, 6443/2379-2380/10250 ← SG 내부
                                   │
      ┌────────────────────────────┴──────────────────────────────┐
      │  EC2 t4g.medium ×3  (2vCPU/4GB, Graviton/arm64)            │
      │                                                            │
      │  node-1  k3s server --cluster-init   ◄── Elastic IP        │
      │  node-2  k3s server --server node-1                        │
      │  node-3  k3s server --server node-1                        │
      │          embedded etcd, quorum 2/3, 전 노드 워크로드 수용   │
      │                                                            │
      │  traefik (k3s 내장)  :80/:443  ── Ingress + TLS 종료        │
      │     └─► gateway-service (ClusterIP)                        │
      │            ├─► user / map / place (ClusterIP)              │
      │  cert-manager ── Let's Encrypt HTTP-01 자동 발급·갱신       │
      │  ArgoCD ── 기존 Application 그대로                          │
      │  kube-prometheus-stack ── Prometheus/Grafana/Alertmanager  │
      │  postgres (StatefulSet, EBS PVC) / redis / rabbitmq        │
      │                                                            │
      │  EBS gp3: 루트 30GB ×3, 데이터 20GB (EBS CSI 동적 프로비저닝) │
      └────────────────────────────────────────────────────────────┘

  S3        사진 버킷 + Terraform state 버킷
  ghcr.io   ghcr.io/moa-map/<svc>:<git-sha>  (퍼블릭 레포 → 무료)
  IAM       인스턴스 프로파일 → S3 접근 (정적 키 제거)
```

### 3.2 네트워크

| 항목 | 설계 | 근거 |
|---|---|---|
| 리전 | ap-northeast-2 | 사용자·팀 위치 |
| AZ | **단일 AZ** | EBS 볼륨은 AZ에 묶인다. 멀티 AZ면 파드가 볼륨 있는 AZ로만 스케줄되어 3노드 의미가 줄고, 크로스 AZ 전송 과금도 생긴다 |
| 서브넷 | public 1개 | NAT Gateway(월 ~$35)를 피하려고 노드를 public에 두고 SG로 막는다 |
| SG | 80/443 공개, 22는 관리자 IP, k3s 포트는 SG 자기참조 | 6443(API), 2379-2380(etcd), 10250(kubelet), 8472/udp(flannel VXLAN) |
| 고정 IP | Elastic IP 1개 → node-1 | DNS 대상. node-1이 죽으면 EIP를 다른 노드로 옮긴다 (수동 또는 스크립트) |

### 3.3 컴퓨트 · 노드 역할

| 노드 | 역할 | 특이 배치 |
|---|---|---|
| node-1 | k3s server (init), EIP 보유 | traefik이 hostPort 80/443 |
| node-2 | k3s server (join) | — |
| node-3 | k3s server (join) | — |

- 인스턴스 타입 `t4g.medium` (arm64). x86(`t3.medium`) 대비 ~20% 저렴. **CI에서 arm64 이미지를 빌드해야 한다** (3.7).
- 메모리 추정: JVM 4개 ~2GB + Postgres/Redis/RabbitMQ ~0.5GB + k3s(etcd 포함) ~0.8GB/노드 + OS ~0.3GB. 3노드 12GB에 넉넉히 들어간다.
- 부족하면 인스턴스 타입 변경(재부팅)으로 대응. 처음부터 크게 잡지 않는다.

**스팟 옵션**: etcd HA라 스팟 1대 회수는 견딘다. `1 온디맨드(Postgres 고정) + 2 스팟` 조합이면 월 ~$48. 2대 동시 회수 시 quorum 상실 → `k3s server --cluster-reset`으로 복구 (4.5).

### 3.4 k3s 구성

```bash
# node-1
curl -sfL https://get.k3s.io | sh -s - server --cluster-init \
  --tls-san <EIP> --tls-san dev-api.moamap.co.kr \
  --disable servicelb            # Elastic IP + traefik hostPort로 대체
# node-2, node-3
curl -sfL https://get.k3s.io | sh -s - server --server https://<node-1-private-ip>:6443 --token <token>
```

| 내장 컴포넌트 | 사용 여부 | 이유 |
|---|---|---|
| containerd | 사용 | |
| flannel (VXLAN) | 사용 | 기본 NetworkPolicy는 됨. Cilium 불필요 |
| CoreDNS | 사용 | |
| **traefik** | **사용** | Ingress Controller. 별도 nginx 설치 불필요 |
| ServiceLB (klipper) | **비활성** | 클라우드 LB가 없으니 hostPort 방식만 남는데, traefik이 이미 그 역할 |
| local-path-provisioner | 보조 | EBS CSI를 기본 StorageClass로. local-path는 임시용 |
| metrics-server | 사용 | HPA·`kubectl top` |

- 데이터스토어: embedded etcd. `--cluster-init`으로 시작해야 나중에 server를 붙일 수 있다 (SQLite로 시작하면 전환 단계가 하나 더 생긴다).
- etcd 스냅샷: k3s 내장 (`--etcd-snapshot-schedule-cron`). S3 업로드 옵션 활성화.

### 3.5 스토리지

| 항목 | 설계 |
|---|---|
| CSI | **AWS EBS CSI Driver** (Helm). 노드 IAM Role에 EBS 권한 |
| StorageClass | `gp3`, `reclaimPolicy: Retain` (NHN과 동일한 안전장치), `volumeBindingMode: WaitForFirstConsumer` |
| Postgres PVC | 20Gi gp3. 파드가 노드를 옮겨도 볼륨이 따라간다 (같은 AZ 내) |
| 백업 | EBS 스냅샷 (수동 또는 AWS Backup) + `pg_dump` → S3 |

NHN의 `cinder-block` PVC와 개념이 같다. 차이는 CSI 드라이버를 **직접 설치**한다는 것뿐.

### 3.6 트래픽 진입 · TLS · DNS

```
클라이언트 → dev-api.moamap.co.kr → EIP:443
          → traefik (TLS 종료, Let's Encrypt 인증서)
          → Ingress rule: host=dev-api.moamap.co.kr → gateway-service:8080 (ClusterIP)
          → 이후는 현재와 동일
```

- `gateway-service` Service: `LoadBalancer` → **`ClusterIP`** 로 변경.
- Ingress 리소스는 `k8s/base/gateway-service/ingress.yaml`에 두고 host는 overlay에서 패치.
- cert-manager `ClusterIssuer`: Let's Encrypt HTTP-01. 스테이징으로 먼저 검증 후 프로덕션 발급 (실패 시 발급 제한 있음).
- ACME 계정 이메일은 `cluster-issuer.example.yaml`만 커밋, 실제 파일은 gitignore (개인 이메일 공개 방지).
- 가비아 DNS 유지. Route53(월 $0.50)은 불필요.
- Swagger·`DEV_LOGIN`은 traefik **Basic Auth** 미들웨어로 보호 (팀원은 URL+비번).

### 3.7 컨테이너 이미지 · CI/CD

| 항목 | 변경 |
|---|---|
| 레지스트리 | NCR → **ghcr.io/moa-map/<svc>** (퍼블릭 레포는 무료, `GITHUB_TOKEN`으로 인증) |
| 아키텍처 | amd64 → **arm64** (`docker buildx build --platform linux/arm64`) |
| GitHub Secrets | `NCR_*` 제거. ghcr.io는 워크플로 `permissions: packages: write`로 충분 |
| imagePullSecret | 퍼블릭 이미지면 불필요. 프라이빗으로 두면 `ghcr-cred` |
| ArgoCD | 재설치 후 `k8s/argocd/*.yaml` 그대로 apply |
| 파이프라인 구조 | **변경 없음** (build → push → newTag 커밋 → ArgoCD 수동 sync) |

### 3.8 설정 · 시크릿

| 항목 | 변경 |
|---|---|
| ConfigMap | `PUBLIC_GATEWAY_URL`, `KAKAO_REDIRECT_URI`를 `https://dev-api.moamap.co.kr` 기반으로 |
| 오브젝트 스토리지 | `OBJECT_STORAGE_ENDPOINT`를 S3로. **코드는 이미 AWS S3 SDK(`S3Presigner`)라 변경 없음** |
| **액세스 키 제거** | EC2 인스턴스 프로파일(IAM Role)로 S3 접근. 코드에서 static credentials → `DefaultCredentialsProvider`로 바꾸면 `OBJECT_STORAGE_ACCESS_KEY/SECRET_KEY`가 **없어진다** (별도 이슈) |
| 그 외 시크릿 | `moamap-secrets` 재생성 (JWT, 카카오, Gemini, 서울 API, DB, RabbitMQ) |

### 3.9 관측 (신규 구축)

| 컴포넌트 | 역할 |
|---|---|
| kube-prometheus-stack (Helm) | Prometheus + Grafana + Alertmanager + node-exporter + kube-state-metrics |
| ServiceMonitor ×4 | 각 서비스 `/actuator/prometheus` 스크랩 (히스토그램 이미 설정됨 → p95 대시보드) |
| Grafana | Ingress로 노출, Basic Auth |
| (선택) Loki + Promtail | 로그 수집 |

현재 로컬 부하테스트용 `load-test/prometheus/prometheus.yml`의 스크랩 설정을 ServiceMonitor로 옮기면 된다.

### 3.10 리소스 매핑표

| NHN (As-Is) | AWS (To-Be) | 변경 성격 |
|---|---|---|
| NKS 관리형 클러스터 | EC2 ×3 + k3s embedded etcd | 직접 운영 |
| 워커 `u2.c2m4` ×2 | `t4g.medium` ×3 | 노드 +1, 아키텍처 arm64 |
| LoadBalancer + 공인 IP | Elastic IP + traefik hostPort | LB 비용 제거 |
| `cinder-block` (Retain) | EBS CSI `gp3` (Retain) | 드라이버 직접 설치 |
| NCR | ghcr.io | 무료화 |
| Object Storage (S3 호환) | S3 | 엔드포인트만 변경 |
| 정적 액세스 키 | IAM 인스턴스 프로파일 | 키 제거 |
| 인터넷 게이트웨이 (수동) | IGW (Terraform) | IaC 범위 확대 |
| StorageClass (수동 apply) | Helm/Terraform | IaC 범위 확대 |
| http | https (cert-manager) | 신규 |
| 모니터링 없음 | kube-prometheus-stack | 신규 |
| ArgoCD | ArgoCD | 동일 |
| kustomize 매니페스트 | 동일 (Service 타입·Ingress 추가) | 최소 변경 |
| 가비아 DNS | 가비아 DNS | 동일 |

### 3.11 비용 추정 (월, 온디맨드, 어림값)

| 항목 | 금액 |
|---|---|
| EC2 t4g.medium ×3 | ~$90 |
| EBS gp3 (30GB×3 + 20GB) | ~$9 |
| Elastic IP (연결 중) | ~$4 |
| S3 (수 GB) | ~$1 |
| ghcr.io, 가비아 DNS | $0 |
| **합계** | **~$105** |

| 절감 옵션 | 금액 |
|---|---|
| 1 온디맨드 + 2 스팟 | ~$60 |
| 3 스팟 | ~$40 |
| Savings Plan 1년 | 온디맨드 대비 -30% |

비교: EKS는 컨트롤플레인만 $73 + 노드. NHN 현재 구성(노드 2 + LB + 볼륨)과는 비슷한 수준이고, 스팟 혼합 시 절반.

---

## 4. 마이그레이션 계획

### 4.1 단계별 로드맵

각 단계 = 이슈 1개 = PR 1개. "배우는 것" 열이 포트폴리오 서사다.

| # | 단계 | 산출물 | 배우는 것 | 과금 |
|---|---|---|---|---|
| 0 | NHN tfstate 소재 확인, AWS 계정·IAM 사용자(최소 권한)·키페어 | — | — | 없음 |
| 1 | `infra/` 재구성 + AWS Terraform 뼈대 (`plan`까지) | `infra/aws/*.tf` | AWS 네트워킹, SG 설계 | 없음 |
| 2 | `terraform apply` — VPC·SG·EC2×3·EIP·EBS | 인스턴스 3대 | — | **AWS 과금 시작** |
| 3 | k3s HA 설치 (cluster-init → join ×2) | 클러스터 | **etcd quorum, 노드 조인, 토큰, kubeconfig** | |
| 4 | EBS CSI 드라이버 + StorageClass | 동적 PV | **CSI, StorageClass, WaitForFirstConsumer, AZ 제약** | |
| 5 | ghcr.io 전환 + CI arm64 빌드 | 워크플로 수정 | multi-arch 이미지 | |
| 6 | ArgoCD 설치 + Application apply + 매니페스트 배포 | 서비스 기동 | GitOps 재현 | |
| 7 | Ingress(traefik) + cert-manager + Basic Auth | https | **Ingress, TLS 종료, ACME, 미들웨어** | |
| 8 | 데이터 이전 (Postgres, 사진) + DNS 절체 | 서비스 전환 | 무중단 절체 | |
| 9 | kube-prometheus-stack + ServiceMonitor + 대시보드 | 관측 | **PromQL, 히스토그램, 알림** | |
| 10 | stateless `replicas: 2` + podAntiAffinity + PDB | 가용성 | **스케줄링, 롤링 업데이트, PDB** | |
| 11 | **NHN 정리** (4.4) | 이중과금 종료 | — | **NHN 과금 종료** |
| 12 | 장애 훈련: `drain`, 노드 강제 종료, EIP 이동, etcd 스냅샷 복구 | 문서 | **DR, 운영** | |

2~11 구간은 **양쪽 동시 과금**이다. 짧게 가져간다.

### 4.2 데이터 이전

**Postgres**

```bash
# NHN 파드에서 덤프 → 로컬 경유 없이 S3로
kubectl -n dev exec postgres-0 -- pg_dump -U moamap -Fc moamap > /dev/stdout | aws s3 cp - s3://<bucket>/migration/moamap.dump
# AWS 파드에서 복원
aws s3 cp s3://<bucket>/migration/moamap.dump - | kubectl -n dev exec -i postgres-0 -- pg_restore -U moamap -d moamap
```

- **덤프에는 실제 사용자 정보(카카오 ID, 닉네임, 이메일)가 들어 있다.** 노트북에 남기지 않는다. S3 객체는 복원 확인 후 삭제. `.gitignore`에 `*.dump`, `*.sql.gz` 추가.
- 스키마 초기화 SQL(`01-schemas.sql`)은 새 클러스터 최초 기동 시 자동 실행되므로, 복원 전에 스키마가 존재한다.

**사진 (Object Storage → S3)**

```bash
rclone sync nhn:<bucket> s3:<bucket>   # 또는 aws s3 sync (S3 호환 엔드포인트 지정)
```

- DB에 저장된 사진 URL이 **절대 URL**(`OBJECT_STORAGE_PUBLIC_BASE_URL` 기반)이라, 도메인이 바뀌면 URL을 일괄 치환해야 한다. `UPDATE ... SET photo_url = REPLACE(photo_url, '<old-base>', '<new-base>')`.
- 사진은 개인 콘텐츠다. 전송 중 공개 버킷에 두지 않는다.

### 4.3 절체 전략

```
1. AWS 클러스터 완전 기동, 자체 확인 (EIP로 직접 호출)
2. Postgres 최종 덤프 직전 NHN 쓰기 차단 (gateway replicas=0 또는 점검 응답)
3. 덤프 → 복원 → 사진 sync → URL 치환
4. 가비아 A 레코드 → EIP (TTL 600이라 10분 내 전파)
5. 카카오 개발자 콘솔 Redirect URI를 https 도메인으로 (기존 것 유지한 채 추가)
6. 검증 후 NHN gateway 유지 (롤백 대비, 1~2일)
7. 안정 확인 → 4.4 진행
```

### 4.4 NHN 정리 절차 — 이중 과금 종료

**순서가 중요하다. 빠뜨리면 계속 과금된다.**

```bash
# 0. tfstate 소재 확인 — 없으면 아래 전부 콘솔 수동
# 1. 볼륨 (Retain이라 3단계 전부 필요)
kubectl -n dev delete statefulset postgres
kubectl -n dev delete pvc data-postgres-0
kubectl delete pv <pv-name>
# 2. LoadBalancer (Service 삭제 → 콘솔에서 LB 목록 확인)
kubectl -n dev delete svc gateway-service
# 3. Terraform 리소스 (NKS, 서브넷, VPC)
cd infra/nhncloud && terraform destroy
# 4. 콘솔 수동 (Terraform 밖)
#    인터넷 게이트웨이, NCR 이미지·레지스트리, Object Storage 버킷, keypair, 플로팅 IP
# 5. 최종: 콘솔 과금 대시보드에서 잔존 리소스 0 확인
```

### 4.5 리스크와 대응

| 리스크 | 대응 |
|---|---|
| NHN tfstate 분실 | 콘솔 수동 정리 + 과금 대시보드로 잔존 확인 |
| arm64 이미지 문제 | 5단계에서 `docker run --platform linux/arm64`로 로컬 검증. 문제 시 `t3.medium`(x86)으로 타입 변경 |
| 스팟 2대 동시 회수 (quorum 상실) | 남은 노드에서 `k3s server --cluster-reset` → 재조인. 12단계 훈련 항목 |
| node-1(EIP) 장애 | EIP를 다른 노드로 재연결. traefik은 DaemonSet이라 어느 노드든 수신 |
| Let's Encrypt 발급 제한 | 스테이징 issuer로 먼저 검증 |
| 사진 URL 치환 누락 | 치환 전후 `SELECT count(*) WHERE photo_url LIKE '<old>%'` 로 0 확인 |
| 이중 과금 장기화 | 2~11단계를 2주 내 완료 목표. 진행 막히면 NHN 먼저 축소(노드 1대로) |

---

## 5. 트레이드오프 · 면접 Q&A

**Q. 왜 EKS를 안 썼나?**
컨트롤플레인 고정비 $73/월이 대회 프로젝트 예산을 넘는다. 관리형의 가치(HA 컨트롤플레인, 자동 업그레이드)는 실사용자 트래픽이 있을 때 값어치가 생기는데, 우리는 그 단계가 아니다. 대신 컨트롤플레인 운영(etcd 스냅샷, 노드 조인, 장애 복구)을 직접 해보는 게 학습 목표에 맞았다.

**Q. k3s는 "가벼운 k8s"라 뭔가 빠진 것 아닌가?**
CNCF 인증 배포판이라 API는 동일하다. 빠진 건 클라우드 프로바이더 통합(LB, CSI를 직접 설치)과 컨트롤플레인이 단일 프로세스라는 점이다. 우리 규모에선 그 "직접 설치"가 오히려 학습 포인트였다.

**Q. kubespray로 진짜 k8s를 세우지 왜 k3s인가?**
kubespray가 더 보여주는 건 apiserver·scheduler가 별도 파드로 보이는 것과 kubeadm 인증서 관리다. 그 대가로 노드당 ~2GB를 컨트롤플레인이 먹고, 인증서 1년 만료·플레이북 업그레이드 실패 같은 운영 부담이 따라온다. 3~4대 예산에서 워크로드를 돌리면서 그걸 감당할 수 없었다. 스케줄링·스토리지·Ingress·관측·장애 대응은 둘이 동일하다.

**Q. 왜 3대인가? 1대면 더 싸지 않나?**
1대는 컨트롤플레인 SPOF이고 노드 장애·drain·롤링 업데이트를 실습할 수 없다. 3대는 etcd quorum(2/3)으로 HA가 성립하는 최소 홀수다. "마스터 1 + 워커 2"는 비용은 3배인데 SPOF가 그대로라 피했다.

**Q. 3대인데 Postgres는 여전히 단일이지 않나?**
맞다. 이번 범위에선 stateless 서비스의 가용성(replicas 2 + anti-affinity)까지만 올리고, Postgres는 EBS CSI로 "노드가 죽어도 볼륨이 살아 다른 노드에서 뜨는" 수준까지다. Postgres HA(스트리밍 복제·Patroni)는 난이도 대비 지금 필요가 없어 뺐다. 필요해지면 RDS로 빼는 게 현실적이다.

**Q. 스팟을 프로덕션에 써도 되나?**
dev/포트폴리오 클러스터라 썼다. etcd HA가 1대 회수를 견디고, 2대 동시 회수는 `cluster-reset`으로 복구 절차를 마련했다. 실사용자가 있다면 최소 컨트롤플레인 quorum(2대)은 온디맨드로 둔다.

**Q. 정적 액세스 키를 없앤 이유는?**
Secret에 평문으로 있는 키는 유출 경로(로그, 덤프, 실수 커밋)가 있다. IAM 인스턴스 프로파일이면 키 자체가 존재하지 않아 유출할 게 없다. AWS로 옮기면서 공짜로 얻는 보안 개선이었다.

**Q. NAT Gateway 없이 노드를 public에 둔 게 괜찮나?**
SG로 인바운드를 80/443/22(관리자 IP)로 제한하면 실질 노출면은 같다. NAT Gateway는 월 $35로 노드 1대 값이다. 프라이빗 서브넷은 규모가 커져 노드가 늘고 감사 요건이 생길 때 도입한다.

**Q. 마이그레이션에서 제일 조심한 건?**
이중 과금과 개인정보. 양쪽 클러스터가 동시에 도는 구간을 최소화했고, NHN 볼륨이 `Retain`이라 PVC를 지워도 과금이 남는 걸 절차에 박았다. DB 덤프에 사용자 정보가 들어가므로 로컬을 거치지 않고 S3 경유 후 삭제했다.

---

## 6. 부록

### 6.1 Terraform 디렉터리 구조 (제안)

```
infra/
├── README.md              환경별 설명 + 마이그레이션 이력
├── nhncloud/              ← 기존 infra/terraform/ 이동. 파일 변경 없음. destroy 후에도 보존
│   ├── network.tf  nks.tf  provider.tf  versions.tf  variables.tf  outputs.tf
│   ├── terraform.tfvars.example  openrc.sample
│   └── README.md
└── aws/
    ├── versions.tf         backend "s3" (state 버킷은 부트스트랩으로 먼저 생성)
    ├── provider.tf         region 변수, 자격증명은 env/profile
    ├── network.tf          VPC, subnet, IGW, route table, SG
    ├── ec2.tf              instance ×3 (for_each), EIP, root/data EBS, user_data(k3s 설치 훅 선택)
    ├── iam.tf              인스턴스 프로파일 (S3, EBS CSI 권한)
    ├── s3.tf               사진 버킷 (퍼블릭 읽기 정책은 prefix 한정)
    ├── variables.tf        instance_type, node_count=3, admin_cidr, key_name
    ├── outputs.tf          EIP, private IPs (kubeconfig 등 민감정보 출력 금지)
    └── terraform.tfvars.example
```

NHN state와 AWS state는 완전히 분리한다. NHN 디렉터리를 지우지 않는 이유: destroy에 필요하고, "이전 구성"이 포트폴리오 서사의 절반이다.

### 6.2 착수 전 체크리스트

- [ ] NHN `.tfstate` 소재 확인 (없으면 4.4를 콘솔 수동으로 계획)
- [ ] AWS 계정 결제 알림 설정 (Budget $50, $100 알림)
- [ ] Terraform용 IAM 사용자 최소 권한 (EC2, VPC, S3, IAM PassRole 한정)
- [ ] 가비아 WHOIS 등록정보 공개 제한 신청 여부 확인
- [ ] `.gitignore`에 `*.dump`, `cluster-issuer.yaml`(실제 파일) 추가
- [ ] 카카오 개발자 콘솔에 https Redirect URI 추가 준비

### 6.3 용어

| 용어 | 뜻 |
|---|---|
| k3s server / agent | 컨트롤플레인 포함 노드 / 워커 전용 노드. server도 기본적으로 워크로드를 받는다 |
| embedded etcd | k3s가 내장한 etcd. `--cluster-init`으로 활성화. 홀수 노드 필요 |
| quorum | etcd 다수결 정족수. 3노드면 2대 생존 필요 |
| CSI | Container Storage Interface. 클라우드 볼륨을 k8s PV로 쓰게 하는 드라이버 |
| Retain | PVC 삭제 시 PV·실제 볼륨을 남기는 정책. 데이터 보호용이나 과금 주의 |
| traefik | k3s 내장 Ingress Controller |
| cert-manager | Let's Encrypt 인증서 자동 발급·갱신 |
| HTTP-01 | ACME 챌린지. 도메인이 서버를 가리켜야 통과 |
| PDB | PodDisruptionBudget. drain 시 최소 가용 파드 수 보장 |
| 인스턴스 프로파일 | EC2에 IAM Role을 붙이는 방법. 키 없이 AWS API 접근 |
