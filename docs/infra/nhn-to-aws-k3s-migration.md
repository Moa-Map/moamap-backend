# 모아맵 인프라 마이그레이션 설계서 — NHN Cloud NKS → AWS k3s

| | |
|---|---|
| 작성일 | 2026-09-07 |
| 이슈 | #105 |
| 기준 커밋 | `develop` @ `5a2e871` |
| 상태 | **설계 확정, 착수 전** |
| 개정 | 2026-09-07 (1) 노드 구성 server 3 → 컨트롤플레인 1 + 데이터플레인 2 (2) 레지스트리 ghcr.io → ECR (3) DB 파드 → **RDS**(프라이빗 서브넷) (4) 비용 검토 끝에 ALB·NAT 미도입 — 노드는 퍼블릭, 진입점은 **EIP + traefik 내장 ACME** |
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

> **비용 전제 주의.** 아래 "$30~90"은 EC2+EBS만 센 값이다. RDS와 퍼블릭 IPv4 과금을 더한 실제 월 비용은 **~$117**이다 (3.11). ALB·NAT Gateway까지 붙인 안($175)도 검토했으나 비용 대비 이득이 맞지 않아 뺐다. 어느 쪽이든 EKS는 컨트롤플레인 $73이 그대로 얹히므로 비교의 결론은 바뀌지 않는다.

**ECS Fargate의 함정**: "서버리스라 싸다"는 상태 저장 서비스에서 깨진다. Postgres·Redis·RabbitMQ를 Fargate에 올릴 수 없어 RDS·ElastiCache·AmazonMQ가 필요한데, AmazonMQ 최소 인스턴스만 월 $30 수준이다.

**kubespray를 안 고른 이유**: k8s 경험의 95%는 컨트롤플레인 *위*에 있다(스케줄링·스토리지·Ingress·관측·장애 대응). kubespray가 더 주는 건 "apiserver를 별도 파드로 본다", "kubeadm 인증서를 직접 관리한다" 둘뿐이고, 그 대가로 노드당 ~2GB를 컨트롤플레인이 먹고 인증서 1년 만료 같은 운영 함정이 따라온다. 3~4대 예산에서 kubespray를 하면 컨트롤플레인 3대 + 워커 1대가 되어 워크로드 넣을 데가 없다. 컨트롤플레인 내부가 궁금하면 대회 후 스팟 3대로 하루 실습한다(수천 원).

### 1.4 노드 구성 결정

**컨트롤플레인 1대 + 데이터플레인 2대.** 컨트롤플레인은 taint를 걸어 워크로드를 받지 않는다.

| 구성 | 컨트롤플레인 | 노드 1대 장애 시 | 워크로드 메모리 | 월 비용 |
|---|---|---|---|---|
| server 1 (워크로드 겸용) | SPOF | 전체 다운 | 4GB | ~$30 |
| **CP 1(small, taint) + worker 2(medium)** | **SPOF** | 워커면 나머지 1대로 축소 / CP면 API 정지 | **8GB** | **~$75** |
| server 3 (etcd HA) | HA | 유지 | 12GB | ~$90 |

etcd HA(server 3대)를 포기하고 이 구성을 고른 이유:

1. **컨트롤플레인/데이터플레인 분리가 실제 k8s 클러스터의 표준 형태다.** 관리형(EKS·NKS)에서 컨트롤플레인이 안 보였던 이유가 여기서 눈에 보인다. taint/toleration, `kubectl describe node`의 taint 필드, "왜 이 노드엔 파드가 안 뜨지"를 직접 겪는다.
2. **컨트롤플레인이 작아도 된다.** 워크로드를 안 받으므로 t4g.small(2vCPU/2GB)로 충분하다 — k3s server + embedded etcd가 ~700MB. 그만큼을 워커 쪽에 쓴다.
3. **비용은 server 3대보다 싸고, 워크로드에 쓸 메모리는 8GB로 확보된다.**

**포기한 것: 컨트롤플레인 HA.** 컨트롤플레인이 죽으면 API·스케줄링·ArgoCD sync가 멈춘다. 다만 **이미 떠 있는 파드와 traefik을 통한 서비스 트래픽은 계속 흐른다** (kubelet과 kube-proxy는 apiserver 없이도 기존 상태를 유지한다). dev/포트폴리오 클러스터에서 감당 가능한 손실이라 판단했다.

- 복구 경로는 남겨둔다: 컨트롤플레인을 `--cluster-init`(embedded etcd)로 시작하므로, 나중에 server를 2대 더 붙이면 SQLite → etcd 전환 없이 그대로 HA가 된다.
- etcd 스냅샷을 S3로 자동 업로드해서, 컨트롤플레인이 통째로 날아가도 새 인스턴스에서 `--cluster-reset-restore-path`로 복원한다 (4.5, 12단계).

**워크로드가 2노드에 들어가는가.** JVM 4개 ~2GB + Redis/RabbitMQ ~0.5GB + k3s agent·OS ~0.6GB/노드 = ~3.7GB. **Postgres가 RDS로 빠져서** 8GB 중 절반이 남는다. 단 ArgoCD(~0.5GB)와 kube-prometheus-stack(~1.5GB)까지 얹으면 ~5.7GB라 여유가 크진 않다 — 9단계에서 Prometheus `retention`·리소스 limit을 좁게 잡는다. 부족하면 워커 타입을 올린다(재부팅).

### 1.5 최종 결정

```
public  서브넷 ×2 (2AZ)   노드 3대 — 공인 IP 보유, IGW로 직접 아웃바운드 (NAT 없음)
  EC2 t4g.small  (2vCPU/2GB, Graviton) × 1   k3s server — taint로 워크로드 차단
  EC2 t4g.medium (2vCPU/4GB, Graviton) × 2   k3s agent  — 앱 + Redis/RabbitMQ
private 서브넷 ×2 (2AZ)   RDS PostgreSQL db.t4g.micro 전용 (인터넷 경로 없음)
노드·RDS 인스턴스는 az-a 한 곳에만 (EBS/비용). 2AZ는 RDS 서브넷 그룹 요건
SG 3개: node(자기참조만) / ingress(80·443, 워커에만) / rds(노드에서만 5432)
  → 마스터는 인터넷에서 열린 포트가 0개. 22는 어디에도 없음 — 접속은 SSM
EIP 1개 → worker-1, traefik이 TLS 종료(Let's Encrypt, traefik 내장 ACME)
S3 (사진 + tfstate, 게이트웨이 엔드포인트), ECR (이미지), 가비아 DNS A 레코드
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

`infra/nhncloud/` (provider `nhn-cloud/nhncloud ~> 1.0`, OpenStack 기반. 2026-09-07 `infra/terraform/`에서 이동)

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
| 3 | HTTPS 없음 | 이전 시 traefik 내장 ACME + Let's Encrypt (4.1 7번) |
| 4 | 모니터링·로그 수집 없음 | 이전 시 kube-prometheus-stack (4.1 9번) |
| 5 | Terraform state 소재 불명 | **정리(4.4) 전 반드시 확인** |
| 6 | 정적 오브젝트 스토리지 키를 Secret에 보관 | 이전 시 IAM Role로 대체 (3.8) |

애플리케이션 레벨 보안 개선 사항은 별도 이슈로 관리한다 (이 문서 범위 아님).

---

## 3. 목표 상태 (To-Be) — AWS + k3s

### 3.1 구성도

```
가비아 DNS  moamap.co.kr  (유지, 비용 0)
   └─ dev-api.moamap.co.kr   A → EIP
                                   │
AWS ap-northeast-2 (서울) ──────────┼──────────────────────────────
  VPC 10.0.0.0/16
   │
   ├ public  10.0.1.0/24 (az-a) · 10.0.2.0/24 (az-c)      ← 인터넷 게이트웨이
   │   ┌──────────────────────────────────────────────────────────┐
   │   │  컨트롤플레인  master  t4g.small (2vCPU/2GB)  [az-a]     │
   │   │    k3s server --cluster-init                             │
   │   │    taint node-role.kubernetes.io/control-plane:NoSchedule │
   │   │    SG: node 만 → 인터넷 인바운드 0개                      │
   │   ├──────────────────────────────────────────────────────────┤
   │   │  데이터플레인  worker-1, worker-2  t4g.medium  [az-a]     │
   │   │    k3s agent.  SG: node + ingress(80/443)                │
   │   │    worker-1 ◄── Elastic IP                               │
   │   │                                                          │
   │   │  traefik (k3s 내장)                                      │
   │   │    :443 TLS 종료 — Let's Encrypt, traefik 내장 ACME      │
   │   │    :80  HTTP-01 챌린지 + https 리다이렉트                │
   │   │     └─► gateway-service (ClusterIP)                      │
   │   │            ├─► user / map / place (ClusterIP)            │
   │   │  redis / rabbitmq (파드)                                 │
   │   │  ArgoCD ── 기존 Application 그대로                        │
   │   │  kube-prometheus-stack ── Prometheus/Grafana/Alertmanager│
   │   └──────────────────────────────────────────────────────────┘
   │
   └ private 10.0.11.0/24 (az-a) · 10.0.12.0/24 (az-c)   ← 인터넷 경로 없음
       ┌──────────────────────────────────────────────────────────┐
       │  RDS PostgreSQL  db.t4g.micro  [az-a]  단일 AZ           │
       │    publicly_accessible=false, 노드 SG에서만 5432          │
       │    자동 백업 7일, 스토리지 암호화                          │
       └──────────────────────────────────────────────────────────┘

  S3        사진 버킷 + Terraform state 버킷 (게이트웨이 엔드포인트 — 무료)
  ECR       <account>.dkr.ecr.ap-northeast-2.amazonaws.com/moamap/<svc>:<git-sha>
  IAM       인스턴스 프로파일 → S3·ECR·EBS·SSM (정적 키 제거)
  SSM       Session Manager — 22 미개방, 베스천 없이 노드 접속·6443 포트포워딩
```

### 3.2 네트워크

| 항목 | 설계 | 근거 |
|---|---|---|
| 리전 | ap-northeast-2 | 사용자·팀 위치 |
| AZ | **서브넷은 2AZ, 실제 배치는 1AZ** | RDS 서브넷 그룹이 2AZ를 강제한다. 하지만 EBS는 AZ에 묶이고 크로스 AZ 전송료가 붙으므로 노드·RDS 인스턴스는 az-a에만 둔다 |
| 노드 위치 | **public 서브넷, 공인 IP 보유** | IGW로 직접 나간다. NAT Gateway(월 ~$44)를 쓰지 않기 위한 선택 |
| RDS 위치 | **private 서브넷** | 아웃바운드가 필요 없어 NAT 없이도 성립한다. 서브넷 자체는 무료라 안 쓸 이유가 없다 |
| 아웃바운드 | IGW 직결 (무료) | 앱이 카카오·Gemini·서울API를 호출해야 하는데, 노드가 퍼블릭이면 NAT가 필요 없다 |
| SG | **3개로 분리** (아래) | |
| 관리자 접속 | **22 미개방. SSM Session Manager** | 키페어도, 베스천도, 관리자 IP 관리도 없앤다. `kubectl`은 6443 포트포워딩 |
| 고정 IP | Elastic IP 1개 → worker-1 | DNS A 레코드 대상 |

**보안 그룹 3개**

| SG | 붙는 대상 | 인바운드 |
|---|---|---|
| `node` | 전 노드 | **자기참조만** — 6443·2379-2380·10250·8472udp를 한 줄로 덮는다 |
| `ingress` | **워커만** | 80·443 ← 0.0.0.0/0 |
| `rds` | RDS | 5432 ← `node` SG |

**마스터는 공인 IP가 있지만 인터넷에서 열린 포트가 0개다.** `node` SG만 붙어서 인바운드 규칙이 자기참조뿐이기 때문이다. 실질 노출은 워커의 80/443뿐이고, 그건 어차피 서비스해야 하는 포트다. 80을 닫지 않는 이유는 Let's Encrypt HTTP-01 챌린지가 그리로 오기 때문이다.

**프라이빗 서브넷 + NAT를 안 쓴 대가.** SG 규칙을 잘못 넣으면 즉시 인터넷에 노출된다 — 프라이빗이었다면 라우팅이 막아줬을 2차 방어선이 없다. 그 방어선의 값이 월 $44(NAT Gateway) 또는 $8(NAT 인스턴스)이었고, 대회 예산에서 전자는 과했고 후자는 SPOF를 하나 더 만드는 일이라 접었다. **대신 SG를 3개로 쪼개 각 노드의 노출면을 최소화**하는 쪽으로 방어를 옮겼다. VPC를 `/16`으로 잡고 프라이빗 대역을 이미 만들어뒀으므로, 나중에 노드를 프라이빗으로 옮기는 건 NAT 추가 + 서브넷 변경으로 끝난다.

### 3.3 컴퓨트 · 노드 역할

| 노드 | 타입 | 위치 | 역할 |
|---|---|---|---|
| master | t4g.small (2vCPU/2GB) | public az-a | k3s server (`--cluster-init`, embedded etcd). **taint `node-role.kubernetes.io/control-plane:NoSchedule`**. SG는 `node`만 → 인바운드 0 |
| worker-1 | t4g.medium (2vCPU/4GB) | public az-a | k3s agent. **EIP 보유** — DNS가 가리키는 진입점 |
| worker-2 | t4g.medium (2vCPU/4GB) | public az-a | k3s agent |

- taint는 k3s 설치 시 `--node-taint`로 건다. 나중에 `kubectl taint`로 거는 것보다 확실하다(재부팅·재설치에도 유지).
- 전부 **arm64(Graviton)**. x86(`t3`) 대비 ~20% 저렴. **CI에서 arm64 이미지를 빌드해야 한다** (3.7).
- **키페어 없음, 22 미개방.** SSM Session Manager로 접속하고, `kubectl`은 6443 포트포워딩으로 쓴다. 6443은 SG에서 자기참조만 허용하므로 `--tls-san`도 필요 없다(포트포워딩은 127.0.0.1로 붙는다).
- 메모리: 앱·Redis/RabbitMQ ~3.7GB, ArgoCD·모니터링까지 ~5.7GB / 8GB (1.4).
- **Postgres는 RDS로 뺐다.** 클러스터 안에 남는 상태 저장 워크로드는 Redis(캐시라 유실 허용)와 RabbitMQ뿐이다.

**스팟은 절체 완료 후에.** 컨트롤플레인은 SPOF라 온디맨드 고정이다. Postgres가 RDS로 빠져서 워커 회수의 타격은 줄었지만(재스케줄로 흡수), 워커가 2대뿐이라 1대 회수 시 남은 1대에 전부 몰린다. 안정화 후 워커 1대만 스팟으로 실험한다.

### 3.4 k3s 구성

```bash
# master (컨트롤플레인)
curl -sfL https://get.k3s.io | K3S_TOKEN=<token> sh -s - server \
  --cluster-init \
  --node-taint node-role.kubernetes.io/control-plane=true:NoSchedule

# worker-1, worker-2 (데이터플레인)
curl -sfL https://get.k3s.io | K3S_TOKEN=<token> K3S_URL=https://<master-private-ip>:6443 sh -s - agent
```

토큰은 Terraform이 `random_password`로 만들어 양쪽 user_data에 넣는다 — 수동 복사 단계가 없다. 대신 **토큰이 tfstate에 남으므로 state 버킷은 반드시 비공개·암호화**다.

| 내장 컴포넌트 | 사용 여부 | 이유 |
|---|---|---|
| containerd | 사용 | |
| flannel (VXLAN) | 사용 | 기본 NetworkPolicy는 됨. Cilium 불필요 |
| CoreDNS | 사용 | |
| **traefik** | **사용** | Ingress Controller. 별도 nginx 설치 불필요 |
| ServiceLB (klipper) | **활성** | traefik 파드가 어느 워커에 있든 **모든 노드의 80/443**을 열어준다. EIP가 붙은 워커가 곧 진입점이 되고, traefik이 어디로 스케줄되든 상관없다. 설정 0줄 |
| local-path-provisioner | 보조 | EBS CSI를 기본 StorageClass로. local-path는 임시용 |
| metrics-server | 사용 | HPA·`kubectl top` |

- 데이터스토어: embedded etcd. server가 1대뿐이라 SQLite로도 되지만, `--cluster-init`으로 시작해야 나중에 server를 붙여 HA로 갈 수 있다 (SQLite로 시작하면 전환 단계가 하나 더 생긴다).
- etcd 스냅샷: k3s 내장 (`--etcd-snapshot-schedule-cron`) + S3 업로드 활성화. **컨트롤플레인이 SPOF이므로 이건 선택이 아니라 필수다.** 스냅샷만 있으면 새 인스턴스에서 `--cluster-reset-restore-path`로 복원된다.

### 3.5 스토리지

| 항목 | 설계 |
|---|---|
| **DB** | **RDS PostgreSQL** `db.t4g.micro`, gp3 20GB(오토스케일 상한 50GB), 암호화, 자동 백업 7일, 단일 AZ |
| CSI | **AWS EBS CSI Driver** (Helm). 노드 IAM Role에 EBS 권한 |
| StorageClass | `gp3`, `reclaimPolicy: Retain`, `volumeBindingMode: WaitForFirstConsumer` |
| 클러스터 내 PVC | traefik `acme.json`(인증서), RabbitMQ(큐 영속화), Prometheus(메트릭 보존). **Postgres PVC는 없어졌다** |
| 백업 | RDS 자동 스냅샷(7일) + 필요 시 `pg_dump` → S3. 사진은 S3 자체 |

Postgres를 RDS로 뺀 대가로 "PVC가 노드를 따라 이동한다"는 학습 소재가 줄었지만, RabbitMQ·Prometheus PVC로 동일한 것을 다룬다. 얻는 것은 자동 백업·PITR·패치와, **워커 노드 장애가 DB 다운으로 이어지지 않는다**는 점이다.

### 3.6 트래픽 진입 · TLS · DNS

```
클라이언트 → dev-api.moamap.co.kr (A 레코드) → EIP:443
          → traefik이 TLS 종료 (Let's Encrypt 인증서, traefik 내장 ACME가 발급·갱신)
          → Ingress rule: host 매칭 → gateway-service:8080 (ClusterIP)
          → 이후는 현재와 동일
```

- `gateway-service` Service: `LoadBalancer` → **`ClusterIP`**. 외부 노출은 traefik Ingress로 모은다.
- **cert-manager를 설치하지 않는다.** Let's Encrypt를 쓰되 ACME 수행은 **traefik 내장 `certResolvers`** 가 한다. cert-manager는 파드 3개(controller/webhook/cainjector)와 CRD를 더 운영해야 하는데, k3s에 이미 들어 있는 traefik이 같은 일을 한다.

```yaml
# k8s/cluster/traefik-config.yaml — k3s가 읽어 내장 traefik에 반영
apiVersion: helm.cattle.io/v1
kind: HelmChartConfig
metadata: { name: traefik, namespace: kube-system }
spec:
  valuesContent: |-
    persistence: { enabled: true, size: 128Mi }   # acme.json 보관 (EBS PVC)
    certResolvers:
      le:
        email: <team-email>
        httpChallenge: { entryPoint: web }
        storage: /data/acme.json
```

Ingress에 `traefik.ingress.kubernetes.io/router.tls.certresolver: le` 를 붙이면 발급되고, 갱신(60일 시점)도 traefik이 자동으로 한다.

**제약 두 가지**

- **traefik `replicas: 1` 고정.** `acme.json`을 여러 파드가 공유할 수 없다(분산 저장은 상용판 기능). 우리는 어차피 1이고, 파드가 다른 워커로 옮겨가도 EBS PVC가 따라간다(같은 AZ).
- **80이 인터넷에서 traefik에 직접 닿아야 한다.** HTTP-01 챌린지 경로다. `ingress` SG가 80을 여는 이유이며, 노드가 퍼블릭이어야 성립하는 조건이기도 하다.

- 발급 실패에는 rate limit(도메인당 주 50회, 실패 5회/시간)이 있다. **스테이징 issuer로 먼저 검증**한 뒤 프로덕션으로 바꾼다.
- DNS는 **A 레코드 → EIP**. 가비아 그대로 쓴다(Route53 불필요).
- Swagger·`DEV_LOGIN`은 traefik **Basic Auth** 미들웨어로 보호한다.

### 3.7 컨테이너 이미지 · CI/CD

| 항목 | 변경 |
|---|---|
| 레지스트리 | NCR → **ECR** `<account>.dkr.ecr.ap-northeast-2.amazonaws.com/moamap/<svc>` (서비스별 리포지토리 4개, Terraform으로 생성) |
| 아키텍처 | amd64 → **arm64** (`docker buildx build --platform linux/arm64`) |
| CI 인증 | `NCR_*` 제거. **GitHub OIDC → IAM Role AssumeRole** (`aws-actions/configure-aws-credentials`). 장수명 액세스 키를 GitHub에 두지 않는다 |
| imagePullSecret | **불필요.** 노드 인스턴스 프로파일에 `AmazonEC2ContainerRegistryReadOnly`를 붙이면 containerd가 ECR에서 바로 pull한다 (k3s는 ECR credential helper 없이도 IMDS 자격증명으로 동작) |
| 이미지 정리 | ECR **lifecycle policy** — 최근 10개만 유지. 커밋 SHA 태그가 무한 누적되는 걸 막는다 (NHN에서 겪은 문제) |
| ArgoCD | 재설치 후 `k8s/argocd/*.yaml` 그대로 apply |
| 파이프라인 구조 | **변경 없음** (build → push → newTag 커밋 → ArgoCD 수동 sync) |

**ghcr.io 대신 ECR을 고른 이유**: 같은 리전 안이라 pull이 빠르고 데이터 전송료가 없다. 무엇보다 **인증이 IAM으로 통일된다** — 노드는 인스턴스 프로파일, CI는 OIDC라 어느 쪽에도 저장된 비밀이 없다(imagePullSecret도, GitHub Secret도). 비용은 프라이빗 500MB/월 무료 + 이후 $0.10/GB-월로, lifecycle policy를 걸면 월 $1 미만이다.

### 3.8 설정 · 시크릿

| 항목 | 변경 |
|---|---|
| ConfigMap | `PUBLIC_GATEWAY_URL`, `KAKAO_REDIRECT_URI`를 `https://dev-api.moamap.co.kr` 기반으로 |
| 오브젝트 스토리지 | `OBJECT_STORAGE_ENDPOINT`를 S3로. **코드는 이미 AWS S3 SDK(`S3Presigner`)라 변경 없음** |
| **DB 접속 정보** | `DB_HOST`를 RDS 엔드포인트로, `DB_PASSWORD`는 Terraform이 생성한 값(`terraform output -raw db_password`). 파드 이름(`postgres`)이 아니라 RDS 주소가 들어간다 |
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
| NKS 관리형 클러스터 | EC2 ×3 + k3s (server 1 + agent 2) | 직접 운영. 컨트롤플레인이 눈에 보인다 |
| 워커 `u2.c2m4` ×2 | CP `t4g.small` ×1 + 워커 `t4g.medium` ×2 | 컨트롤플레인 분리, arm64 |
| (컨트롤플레인 NHN 무료) | **CP도 우리 과금** (~$15/월) | 관리형에서 안 보이던 비용 |
| 서브넷 1개 (SG 1개) | **public ×2(노드) + private ×2(RDS)** + **SG 3개 분리** | 마스터 인바운드 0, DB는 인터넷 경로 없음 |
| LoadBalancer + 공인 IP | Elastic IP + traefik(ServiceLB) | 관리형 LB 비용 제거 |
| Postgres StatefulSet + PVC | **RDS PostgreSQL** | 자동 백업·패치. 워커 장애 ≠ DB 장애 |
| `cinder-block` (Retain) | EBS CSI `gp3` (Retain) — RabbitMQ·Prometheus용 | 드라이버 직접 설치 |
| NCR + `ncr-cred` Secret | ECR + IAM 인스턴스 프로파일 | imagePullSecret 제거 |
| Object Storage (S3 호환) | S3 (+ 게이트웨이 엔드포인트) | 엔드포인트만 변경 |
| 정적 액세스 키 | IAM 인스턴스 프로파일 | 키 제거 |
| SSH (keypair, 22 개방) | **SSM Session Manager** | 키·포트 제거 |
| 인터넷 게이트웨이 (수동) | IGW·NAT·라우팅 (Terraform) | IaC 범위 확대 |
| StorageClass (수동 apply) | Helm/Terraform | IaC 범위 확대 |
| http | https (Let's Encrypt, **traefik 내장 ACME**) | 신규. cert-manager 불필요 |
| DNS A 레코드 | A 레코드 → EIP | 동일 |
| 모니터링 없음 | kube-prometheus-stack | 신규 |
| ArgoCD | ArgoCD | 동일 |
| kustomize 매니페스트 | 동일 (Service 타입·Ingress 추가, DB 호스트 변경) | 최소 변경 |
| 가비아 DNS | 가비아 DNS | 동일 |

### 3.11 비용 추정 (월, 온디맨드, 서울 리전, 730시간)

전부 어림값이다. 확정 전 AWS Pricing Calculator로 검증한다.

| 분류 | 항목 | 단가 | 수량 | 월 |
|---|---|---|---|---|
| **컴퓨트** | | | | **$75.9** |
| | EC2 t4g.small (컨트롤플레인) | $0.0208/h | 1대 | $15.2 |
| | EC2 t4g.medium (데이터플레인) | $0.0416/h | 2대 | $60.7 |
| **스토리지** | EBS gp3 | $0.0912/GB·월 | 95GB | **$8.7** |
| | ├ master 루트 | | 20GB | $1.8 |
| | ├ worker 루트 | | 30GB×2 | $5.5 |
| | └ PVC (acme.json·RabbitMQ·Prometheus) | | 15GB | $1.4 |
| **네트워킹** | | | | **$11.0** |
| | **퍼블릭 IPv4** (노드 3대, EIP 포함) | $0.005/h | 3개 | $11.0 |
| | IGW · S3 게이트웨이 엔드포인트 | 무료 | | $0 |
| | 인터넷 아웃바운드 전송 | 월 100GB 무료 | | $0 |
| **RDS** | | | | **$19.4** |
| | db.t4g.micro (단일 AZ) | ~$0.023/h | 1대 | $16.8 |
| | gp3 스토리지 | $0.131/GB·월 | 20GB | $2.6 |
| | 자동 백업 | DB 크기까지 무료 | 20GB | $0 |
| **기타** | S3 (사진 수 GB) | $0.025/GB·월 | | ~$1 |
| | ECR (lifecycle 10개 유지) | $0.10/GB·월 | ~10GB | ~$1 |
| | Let's Encrypt · 가비아 DNS | 무료 | | $0 |
| **합계** | | | | **~$117** |

**놓치기 쉬운 항목: 퍼블릭 IPv4.** 2024년 2월부터 모든 공인 IPv4가 시간당 $0.005 과금된다. 노드 3대면 월 $11로, RDS 스토리지보다 크다. 노드를 프라이빗으로 옮기면 이 $11은 사라지지만 NAT 비용($8~44)이 그보다 크거나 비슷해서, **이 규모에선 프라이빗이 비용상 이득이 아니다**.

#### 검토했다 채택하지 않은 안

| 안 | 월 | 왜 안 골랐나 |
|---|---|---|
| **ALB + ACM** 도입 | +$25 | ACM 자동 갱신(만료 사고 0)과 워커 장애 시 자동 페일오버를 얻지만, traefik 내장 ACME가 갱신을 대신하고 워커 장애는 EIP 수동 이동으로 감당 가능하다고 봤다. **가용성 요구가 올라가면 1순위 도입 대상** |
| **프라이빗 서브넷 + NAT Gateway** | +$44 | 노드에 공인 IP가 없어져 SG 실수가 즉시 노출로 이어지지 않는다. 방어 한 겹에 월 $44는 대회 예산에서 과했다 |
| 프라이빗 + **NAT 인스턴스**(t4g.nano) | +$8 | 같은 이득을 $8에 얻지만 SPOF와 직접 패치가 붙는다. 프라이빗으로 갈 거면 이쪽 |
| **Cloudflare** 프록시/Tunnel 앞단 | -$0~3 | 무료 TLS·DDoS·오리진 은닉을 얻지만 DNS를 Cloudflare로 옮겨야 하고 진입 경로가 AWS 밖 의존이 된다 |
| **cert-manager** | $0 | 비용이 아니라 운영 문제. 파드 3개 + CRD를 더 두는 대신 k3s에 이미 있는 traefik으로 해결했다 |
| 워커 2대 **스팟** | -$36 | 절체 완료 전까지는 안 쓴다. 워커가 2대뿐이라 1대 회수 시 남은 1대에 전부 몰린다 |
| **Savings Plans 1년** (EC2 -30%) | -$23 | 청구서의 35%(EBS·IPv4·RDS 스토리지·S3)는 약정 대상이 아니고, 무엇보다 **1년 락인**이다. 구성이 굳고 존속이 확실해진 뒤 베이스라인만 약정한다 |

**예산이 더 줄어야 하면 순서는 스팟 → Savings Plans.** 반대로 여유가 생기면 ALB부터 도입한다.

착수 전에 **AWS Budget 알림($100 / $150)** 을 걸고, **계정이 구 프리 티어(12개월) 대상인지 확인**한다 — 대상이면 RDS db.t4g.micro 750시간이 무료라 월 ~$17이 빠진다.

비교: 같은 구성을 EKS로 하면 컨트롤플레인 $73이 그대로 얹혀 ~$190이다.

---

## 4. 마이그레이션 계획

### 4.1 단계별 로드맵

각 단계 = 이슈 1개 = PR 1개. "배우는 것" 열이 포트폴리오 서사다.

| # | 단계 | 산출물 | 배우는 것 | 과금 |
|---|---|---|---|---|
| 0 | NHN tfstate 소재 확인, AWS 계정·IAM 사용자(최소 권한)·키페어 | — | — | 없음 |
| 1 | `infra/` 재구성 + AWS Terraform 뼈대 (`plan`까지) | `infra/aws/*.tf` | AWS 네트워킹, SG 설계 | 없음 |
| 2 | `terraform apply` — VPC(public/private)·SG ×3·EC2×3·EIP·RDS·ECR·S3·IAM | 인프라 일체 | **서브넷 분리, SG 분리 설계, IGW 라우팅** | **AWS 과금 시작** |
| 3 | k3s 설치 (server 1 + agent 2, CP taint) + SSM 접속 확인 | 클러스터 | **CP/DP 분리, taint·toleration, 노드 조인, SSM 포트포워딩 kubeconfig** | |
| 4 | EBS CSI 드라이버 + StorageClass (RabbitMQ·Prometheus용) | 동적 PV | **CSI, StorageClass, WaitForFirstConsumer, AZ 제약** | |
| 5 | ECR 전환 + GitHub OIDC + CI arm64 빌드 | 워크플로 수정 | **OIDC 페더레이션, multi-arch 이미지, lifecycle policy** | |
| 6 | ArgoCD 설치 + Application apply + 매니페스트 배포 | 서비스 기동 | GitOps 재현 | |
| 7 | Ingress(traefik) + **내장 ACME로 Let's Encrypt 발급**(스테이징→프로덕션) + Basic Auth | https | **Ingress, TLS 종료, ACME HTTP-01, rate limit, 미들웨어** | |
| 8 | 데이터 이전 (Postgres→**RDS**, 사진→S3) + DNS 절체(A → EIP) | 서비스 전환 | 무중단 절체 | |
| 9 | kube-prometheus-stack + ServiceMonitor + 대시보드 | 관측 | **PromQL, 히스토그램, 알림** | |
| 10 | stateless `replicas: 2` + podAntiAffinity + PDB | 가용성 | **스케줄링, 롤링 업데이트, PDB** | |
| 11 | **NHN 정리** (4.4) | 이중과금 종료 | — | **NHN 과금 종료** |
| 12 | 장애 훈련: 워커 `drain`·강제 종료 + **EIP 이동**, **컨트롤플레인 재생성 + etcd 스냅샷 복원**, RDS 스냅샷 복원 | 문서 | **DR, 운영** | |

2~11 구간은 **양쪽 동시 과금**이다. 짧게 가져간다.

### 4.2 데이터 이전

**Postgres**

```bash
# NHN 파드에서 덤프 → 로컬 경유 없이 S3로
kubectl -n dev exec postgres-0 -- pg_dump -U moamap -Fc moamap > /dev/stdout | aws s3 cp - s3://<bucket>/migration/moamap.dump

# 복원은 AWS 워커에서 RDS로 (RDS는 프라이빗 서브넷이라 VPC 안에서만 닿는다)
kubectl -n dev run pgrestore --rm -it --restart=Never --image=postgres:16-alpine -- sh -c \
  'aws s3 cp s3://<bucket>/migration/moamap.dump - | pg_restore -h <rds-endpoint> -U moamap -d moamap'
```

- **RDS에는 `01-schemas.sql` 자동 실행이 없다.** 파드 Postgres의 initdb 훅으로 만들어지던 스키마를 복원 전에 직접 한 번 실행해야 한다.

- **덤프에는 실제 사용자 정보(카카오 ID, 닉네임, 이메일)가 들어 있다.** 노트북에 남기지 않는다. S3 객체는 복원 확인 후 삭제. `.gitignore`에 `*.dump`, `*.sql.gz` 추가.
- RDS 마스터 사용자·DB는 Terraform이 만든다. 애플리케이션 전용 계정을 따로 둘지는 별도 이슈.

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
4. 가비아 A 레코드를 **EIP로 변경** (TTL 600이라 10분 내 전파). 전파 후 Let's Encrypt 발급이 가능해진다 — HTTP-01은 도메인이 이 서버를 가리켜야 통과한다
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
| **컨트롤플레인 장애 (SPOF)** | 기존 파드·서비스 트래픽은 계속 흐른다. etcd 스냅샷(S3)에서 새 인스턴스로 `--cluster-reset-restore-path` 복원. 12단계 훈련 항목 |
| 워커 1대 장애 | 남은 1대로 재스케줄되지만 **8GB → 4GB라 전부는 못 뜬다** — 모니터링부터 Pending. PriorityClass로 순서를 정해둔다 |
| **worker-1(EIP) 장애** | **자동 페일오버가 없다.** EIP를 worker-2로 옮겨야 서비스가 돌아온다(association 수정 후 apply, 또는 콘솔). 12단계 훈련 항목이자 ALB를 안 쓴 대가 |
| AZ 장애 | 노드·RDS가 전부 az-a라 전체 다운. 이 예산에서는 감수한다 |
| RDS 장애 | 단일 AZ라 자동 페일오버 없음. 스냅샷 복원(수십 분) 또는 `multi_az=true`로 전환(요금 2배) |
| **인증서 갱신 실패** | traefik이 60일 시점에 자동 갱신하지만 실패하면 90일째 만료된다. Prometheus로 **인증서 만료일 알림**을 걸어둔다(9단계). `acme.json` PVC 유실 시 재발급 — rate limit 주의 |
| Let's Encrypt rate limit | 도메인당 주 50회, 실패 5회/시간. **스테이징 issuer로 먼저 검증**한 뒤 프로덕션으로 바꾼다 |
| SG 오설정 | 노드가 퍼블릭이라 규칙 실수가 즉시 노출이다. `node`/`ingress` SG를 분리해 워커에만 80/443을 열고, 변경은 반드시 Terraform으로만 한다(콘솔 직접 수정 금지) |
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

**Q. 왜 컨트롤플레인 1대인가? SPOF 아닌가?**
맞다, SPOF다. etcd HA(server 3대)와 저울질했고 이쪽을 골랐다. 이유는 세 가지다. (1) 컨트롤플레인이 죽어도 **이미 떠 있는 파드와 서비스 트래픽은 계속 흐른다** — 멈추는 건 API·스케줄링·배포지, 사용자 요청이 아니다. dev 클러스터에서 감당 가능한 손실이다. (2) 컨트롤플레인/데이터플레인을 taint로 분리한 형태가 실제 클러스터의 표준이고, 관리형에서 안 보이던 부분이 여기서 보인다. (3) 컨트롤플레인을 워크로드에서 떼면 t4g.small로 충분해져서, 같은 예산으로 **워크로드 메모리를 8GB 확보**한다. 대신 복구 경로를 붙였다 — `--cluster-init`으로 시작해 나중에 server를 붙이면 그대로 HA가 되고, etcd 스냅샷을 S3에 올려 컨트롤플레인이 통째로 날아가도 복원된다.

**Q. 컨트롤플레인에 taint를 왜 거나? 파드도 받으면 자원이 남지 않나?**
2GB짜리 노드에 워크로드를 받으면 apiserver·etcd가 메모리 경합에 휘말린다. 컨트롤플레인이 흔들리면 클러스터 전체가 흔들리므로, 여기만은 자원을 나눠 쓰지 않는다. k3s는 기본이 "server도 워크로드를 받는다"이므로 `--node-taint`로 명시적으로 껐다.

**Q. DB를 왜 RDS로 뺐나? 파드로 띄우면 월 $18을 아끼는데.**
파드 Postgres에서 아끼는 $18의 대가가 백업·복구·버전 패치 전부를 직접 하는 것이다. 그리고 워커 노드 하나가 죽으면 DB가 같이 죽는다 — 워커가 2대뿐이라 이게 실제로 자주 일어날 상황이다. RDS는 자동 스냅샷(7일)·PITR·마이너 버전 자동 패치를 가져오고, **DB 가용성을 노드 장애와 분리**한다. 개인정보가 든 데이터를 다루면서 백업 전략이 "수동 `pg_dump`"인 건 감당할 수 없다고 판단했다. Redis(캐시라 유실 허용)와 RabbitMQ는 그대로 파드다 — ElastiCache·AmazonMQ까지 가면 월 $60~가 더 붙는데 거긴 그만한 값이 없다.

**Q. 그럼 k8s 스토리지는 안 배우게 되는 것 아닌가?**
RabbitMQ와 Prometheus PVC가 남아서 EBS CSI·StorageClass·`WaitForFirstConsumer`·AZ 제약은 그대로 다룬다. 빠진 건 "DB를 직접 운영한다"는 항목 하나고, 그건 실무에서도 대부분 관리형으로 가는 쪽이다.

**Q. RDS가 단일 AZ면 결국 SPOF 아닌가?**
맞다. Multi-AZ는 요금이 2배(+$18)라 이번 예산에서 뺐다. 대신 자동 스냅샷과 PITR이 있어 **데이터 유실**은 막힌다 — 잃는 건 가용성이지 데이터가 아니다. 파드 Postgres 시절과 비교하면 그것만으로도 순이익이다. 실사용자가 생기면 `multi_az = true` 한 줄이다.

**Q. 스팟을 쓰나?**
절체가 끝날 때까지는 안 쓴다. 컨트롤플레인은 SPOF라 온디맨드 고정이다. Postgres가 RDS로 빠져서 워커 회수의 타격은 줄었지만, 워커가 2대뿐이라 1대 회수 시 남은 1대에 전부 몰린다. 안정화 후 워커 1대만 스팟으로 바꿔 월 ~$15를 아끼는 정도가 적정선이다.

**Q. 왜 ghcr.io가 아니라 ECR인가?**
같은 리전이라 pull이 빠르고 전송료가 없다. 결정적인 건 인증 통일이다 — 노드는 인스턴스 프로파일로, CI는 GitHub OIDC로 붙어서 **imagePullSecret도 GitHub에 저장한 액세스 키도 없어진다**. NHN에서 `ncr-cred` Secret과 `NCR_ACCESS_KEY`를 관리하던 게 통째로 사라진다. 비용은 lifecycle policy(최근 10개 유지)를 걸면 월 $1 미만이다.

**Q. 정적 액세스 키를 없앤 이유는?**
Secret에 평문으로 있는 키는 유출 경로(로그, 덤프, 실수 커밋)가 있다. IAM 인스턴스 프로파일이면 키 자체가 존재하지 않아 유출할 게 없다. AWS로 옮기면서 공짜로 얻는 보안 개선이었다.

**Q. 노드를 퍼블릭 서브넷에 두는 게 괜찮나?**
프라이빗 + NAT를 검토했고 비용 때문에 접었다. NAT Gateway는 월 $44 — 워커 1.5대 값이고, NAT 인스턴스로 내려도 $8에 SPOF가 하나 는다. 반면 얻는 건 "SG 실수를 라우팅이 한 번 더 막아준다"는 2차 방어선이다. 그 대신 **SG를 3개로 쪼개** 노출면을 줄였다 — 마스터에는 `node` SG(자기참조만)만 붙어서 공인 IP가 있어도 인터넷에서 열린 포트가 0개고, 80/443은 워커에만 열린다. 실질 노출은 어차피 서비스해야 하는 포트뿐이다. 다만 이건 **"SG를 항상 Terraform으로만 관리한다"는 규율에 기대는 설계**라, 콘솔 직접 수정을 금지 규칙으로 박아뒀다. 가용성·보안 요구가 올라가면 프라이빗 + NAT 인스턴스가 첫 번째 승급 대상이다.

**Q. 그럼 프라이빗 서브넷은 왜 만들었나?**
RDS 때문이다. RDS는 아웃바운드가 필요 없어서 **NAT 없이도 프라이빗에 둘 수 있다** — 서브넷 자체는 무료라 안 쓸 이유가 없다. `publicly_accessible=false`와 SG(노드에서만 5432)가 실제 차단 장치이고, 프라이빗 서브넷은 라우팅 레벨의 한 겹을 더한다. 개인정보가 든 DB에는 그만한 값이 있다. 겸사겸사 나중에 노드를 프라이빗으로 옮길 자리도 미리 잡힌 셈이다.

**Q. ALB를 왜 안 썼나?**
월 $25(시간당 요금 + 퍼블릭 IPv4 2개)를 내고 얻는 게 ACM 자동 갱신과 워커 장애 시 자동 페일오버인데, 전자는 traefik 내장 ACME가 대신하고 후자는 EIP 수동 이동으로 감당 가능하다고 봤다 — dev/포트폴리오 클러스터라 새벽 장애 대응 SLA가 없다. **정직하게 말하면 이건 가용성을 돈으로 바꾼 결정이고, 실사용자가 생기면 제일 먼저 되돌릴 항목이다.** ALB 도입은 진입점만 바꾸는 국소 변경이라 나중에도 싸다.

**Q. cert-manager 대신 traefik 내장 ACME를 쓴 이유는?**
둘 다 Let's Encrypt에서 인증서를 받아오는 도구고, 차이는 운영 부담이다. cert-manager는 파드 3개(controller/webhook/cainjector)와 CRD를 더 얹는데, k3s에 이미 들어 있는 traefik이 `certResolvers` 설정 몇 줄로 같은 일을 한다. 우리처럼 도메인 하나에 Ingress 몇 개인 규모에서 cert-manager는 과하다. 대가는 traefik `replicas: 1` 제약(acme.json을 공유할 수 없다)인데, 어차피 1이라 문제가 안 됐다. Ingress가 늘고 인증서를 여러 개 다루게 되면 cert-manager로 옮기는 게 맞다.

**Q. Cloudflare를 앞에 두면 무료 TLS에 DDoS 방어까지 되는데 왜 안 썼나?**
실제로 검토했다. 무료 플랜만으로 TLS·DDoS 방어·오리진 은닉을 얻고, SG를 Cloudflare IP 대역으로 좁히면 퍼블릭 노드의 최대 약점도 줄어든다. Tunnel까지 가면 인바운드 포트 0개·공인 IP 0개로 EIP도 필요 없어져 비용이 더 낮다. 안 고른 이유는 두 가지다. (1) DNS를 가비아에서 Cloudflare로 옮겨야 하고, 트래픽 진입 경로 전체가 AWS 밖 서비스에 의존하게 된다 — 장애 시 원인 절단면이 하나 늘어난다. (2) 이번 이관의 목표가 "AWS 위에서 인프라를 직접 구성해보는 것"인데, 진입점을 통째로 위임하면 SG 설계·TLS·DNS라는 학습 대상이 사라진다. **다만 이건 뒤집힐 수 있는 결정이다** — 퍼블릭 노드 노출이 실제로 문제가 되면(스캐너 트래픽, 봇) 비용 없이 방어를 얹는 가장 빠른 수단이 Cloudflare다.

**Q. 1년 약정(Savings Plans)으로 더 싸게 할 수 있지 않나?**
있지만 지금은 아니다. 청구서의 35%(EBS·퍼블릭 IPv4·RDS 스토리지·S3·ECR)는 애초에 약정 대상이 아니라, EC2에 -30%를 적용해도 절감액은 월 $23이다. 그리고 대회 프로젝트라 1년 존속이 확실하지 않은데 **중도 해지가 안 된다** — 3개월 쓰고 내리면 남은 9개월치를 그대로 낸다. 절체 후 2~4주 실사용으로 베이스라인이 굳고 계속 쓸 게 확실해지면, 항상 켜둘 최소치(70% 정도)만 약정하는 게 맞다. 참고로 RDS는 Savings Plans 대상이 아니라 예약 인스턴스를 따로 사야 한다.

**Q. 마이그레이션에서 제일 조심한 건?**
이중 과금과 개인정보. 양쪽 클러스터가 동시에 도는 구간을 최소화했고, NHN 볼륨이 `Retain`이라 PVC를 지워도 과금이 남는 걸 절차에 박았다. DB 덤프에 사용자 정보가 들어가므로 로컬을 거치지 않고 S3 경유 후 삭제했다.

---

## 6. 부록

### 6.1 Terraform 디렉터리 구조

```
infra/
├── README.md              환경별 설명 + 마이그레이션 이력
├── nhncloud/              ← 기존 infra/terraform/ 에서 이동 완료. 파일 변경 없음. destroy 후에도 보존
│   ├── network.tf  nks.tf  provider.tf  versions.tf  variables.tf  outputs.tf
│   ├── terraform.tfvars.example  openrc.sample
│   └── README.md
└── aws/
    ├── versions.tf         backend "s3" (state 버킷은 부트스트랩으로 먼저 생성)
    ├── provider.tf         region 변수, 자격증명은 env/profile
    ├── network.tf          VPC, public/private subnet ×2, IGW, route table, SG ×3(node/ingress/rds), S3 엔드포인트
    ├── ec2.tf              master ×1(taint) + worker ×N, EIP, 조인 토큰, user_data(k3s 설치)
    ├── rds.tf              subnet group, postgres 인스턴스, 마스터 비밀번호
    ├── iam.tf              인스턴스 프로파일 (S3, ECR pull, EBS CSI, SSM 권한)
    ├── s3.tf               사진 버킷 (공개 차단 — 조회는 presigned URL)
    ├── ecr.tf              서비스별 리포지토리 ×4 + lifecycle policy, CI용 OIDC Role
    ├── variables.tf        master/worker instance_type, worker_count=2, azs, db_*
    ├── outputs.tf          EIP, 인스턴스 ID, RDS 엔드포인트·비밀번호 (k3s 토큰·kubeconfig는 출력 금지)
    └── terraform.tfvars.example
```

NHN state와 AWS state는 완전히 분리한다. NHN 디렉터리를 지우지 않는 이유: destroy에 필요하고, "이전 구성"이 포트폴리오 서사의 절반이다.

### 6.2 착수 전 체크리스트

- [ ] NHN `.tfstate` 소재 확인 (없으면 4.4를 콘솔 수동으로 계획)
- [ ] AWS 계정 결제 알림 설정 (**Budget $100 / $150 알림** — 예상 월 ~$175)
- [ ] **구 프리 티어(12개월) 대상 계정인지 확인** — 대상이면 RDS db.t4g.micro 750시간 무료로 월 ~$17 절감
- [ ] Terraform용 IAM 사용자 최소 권한 (EC2, VPC, S3, ECR, IAM PassRole 한정)
- [ ] 가비아 WHOIS 등록정보 공개 제한 신청 여부 확인
- [ ] `.gitignore`에 `*.dump`, `cluster-issuer.yaml`(실제 파일) 추가
- [ ] 카카오 개발자 콘솔에 https Redirect URI 추가 준비
- [ ] Let's Encrypt **스테이징 issuer로 먼저 발급 검증** (프로덕션은 실패 5회/시간 제한)
- [ ] 로컬에 AWS CLI + **Session Manager 플러그인** 설치 (노드 접속·kubectl 포트포워딩에 필수)

### 6.3 용어

| 용어 | 뜻 |
|---|---|
| k3s server / agent | 컨트롤플레인 포함 노드 / 워커 전용 노드. server도 **기본적으로는** 워크로드를 받는다 → taint로 껐다 |
| taint / toleration | 노드가 거는 "이런 파드만 받는다" 표시 / 파드가 그걸 견디겠다는 선언. 컨트롤플레인 분리의 수단 |
| embedded etcd | k3s가 내장한 etcd. `--cluster-init`으로 활성화. HA로 가려면 홀수 노드 필요 |
| quorum | etcd 다수결 정족수. 3노드면 2대 생존 필요. **현재는 server 1대라 quorum 개념이 없다**(HA 전환 시 등장) |
| CSI | Container Storage Interface. 클라우드 볼륨을 k8s PV로 쓰게 하는 드라이버 |
| Retain | PVC 삭제 시 PV·실제 볼륨을 남기는 정책. 데이터 보호용이나 과금 주의 |
| traefik | k3s 내장 Ingress Controller |
| HTTP-01 | ACME 챌린지 방식. 도메인이 이 서버의 80을 가리켜야 통과. traefik이 자동 처리 |
| PDB | PodDisruptionBudget. drain 시 최소 가용 파드 수 보장 |
| 인스턴스 프로파일 | EC2에 IAM Role을 붙이는 방법. 키 없이 AWS API 접근 |
| ServiceLB (klipper) | k3s 내장 LB. `LoadBalancer` Service를 전 노드 hostPort로 노출 |
| certResolver | traefik 내장 ACME 클라이언트. `acme.json`에 인증서를 저장하고 자동 갱신 |
| IGW / NAT Gateway | IGW는 VPC의 인터넷 출입구(무료, 공인 IP 있는 리소스만). NAT는 공인 IP 없는 사설 리소스가 나갈 때 쓰는 대리인(유료, 아웃바운드만) |
| 퍼블릭 IPv4 과금 | 2024년 2월부터 모든 공인 IPv4에 시간당 $0.005. EIP·자동할당 구분 없음 |
| VPC 엔드포인트 | VPC에서 AWS 서비스로 직접 가는 경로. S3 게이트웨이형은 무료 |
| SSM Session Manager | 공인 IP·22 없이 인스턴스 셸·포트포워딩. IAM으로 인가 |
| 서브넷 그룹 | RDS가 쓸 서브넷 묶음. 2AZ 이상 필수 (인스턴스는 1AZ에 뜬다) |
| ECR lifecycle policy | 오래된 이미지를 자동 삭제하는 규칙. 커밋 SHA 태그 누적 방지 |
| GitHub OIDC | GitHub Actions가 단기 토큰으로 IAM Role을 맡는 방식. 저장된 액세스 키 불필요 |
