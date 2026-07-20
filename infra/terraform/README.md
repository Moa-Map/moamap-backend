# Moamap 인프라 (Terraform · NHN Cloud)

NHN Cloud에 Moamap 백엔드를 올릴 인프라를 Terraform으로 관리한다.
Provider는 [`nhn-cloud/nhncloud`](https://registry.terraform.io/providers/nhn-cloud/nhncloud/latest)(OpenStack 기반)를 사용한다.

## Terraform이 관리하는 것
- VPC / 라우팅테이블 / 서브넷 (`network.tf`)
- NKS(쿠버네티스) 클러스터 + 기본 노드그룹 (`nks.tf`)

## Terraform이 관리하지 않는 것 (provider 미지원 → 콘솔에서 수동 생성 후 참조)
- **관리형 PostgreSQL / Redis** — 콘솔 생성, 또는 클러스터 내 배포(추후 결정)
- **NCR(컨테이너 레지스트리)** — 콘솔 생성 → 배포 시 imagePullSecret으로 참조
- **Object Storage 버킷** — remote state 저장용, 콘솔에서 먼저 생성

## 사전 준비 (콘솔에서 수동)
1. NHN Cloud **API 계정**(username/password) + **테넌트 ID**, 리전 확인
2. 노드 접속용 **keypair** 생성
3. `flavor_id`, `node_image_id`, `external_network_id`, `external_subnet_id` 등 ID 확인
4. (원격 state 쓸 경우) Object Storage **버킷** 생성

## 사용법
```bash
# 1) 인증 정보 주입 (openrc.sample → openrc 복사 후 값 채우기)
cp openrc.sample openrc && vi openrc
source openrc

# 2) 변수 값 채우기 (example → tfvars 복사 후 REPLACE_ME 채우기)
cp terraform.tfvars.example terraform.tfvars && vi terraform.tfvars

# 3) 초기화 / 검증 / 미리보기 (여기까지는 자원 생성·과금 없음)
terraform init
terraform validate
terraform plan

# 4) 실제 생성 (⚠️ 과금 시작) — plan 확인 후에만
terraform apply
```

## ⚠️ 보안 · 비용 주의
- **`terraform.tfvars`, `openrc`, `*.tfstate`는 절대 커밋 금지** (`.gitignore`에 차단됨). state에는 비밀이 평문으로 남는다.
- 인증 정보는 코드가 아니라 **openrc 환경변수로만** 주입한다.
- `apply` 시점부터 **NKS 노드·LB 등 과금 발생**. 항상 `plan`으로 먼저 확인하고, 미사용 시 `terraform destroy`.
- `node_count`는 과금 직결 — 작게 시작한다.

## 참고
- 이 코드는 provider 공식 문서 기준으로 작성했고 `terraform validate`로 스키마 검증했으나,
  실제 환경 값(ID)·`plan`/`apply`는 NHN 계정에서 팀이 검증해야 한다.
