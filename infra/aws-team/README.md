# infra/aws-team — 팀 계정(B) EC2 + Docker Compose + CloudFront

배경은 이슈 #155. 이전 계정(A)의 k3s 스택은 `infra/aws/`에 그대로 있다 — **A 정리(destroy)가 끝나기 전까지 지우지 않는다.**

```
Cloudflare DNS (웹 moamap.co.kr·www는 Workers, 건드리지 않음)
  api.moamap.co.kr     CNAME → CloudFront(prod API)  ─HTTP + X-Origin-Verify─▶ prod EC2 (온디맨드) ─▶ RDS
  api-dev.moamap.co.kr CNAME → CloudFront(dev API)   ─HTTP + X-Origin-Verify─▶ dev EC2 (스팟, 안에 Postgres)
  사진: *.cloudfront.net (OAC) → S3 비공개 버킷 (환경별)
```

- 서버 SG는 CloudFront 원본 IP 목록의 80만 허용. 22 미개방, 접속은 SSM.
- API CloudFront 앞에 WAF: IP당 5분 1,000회 초과 시 차단(`waf_rate_limit_per_5min`).
- 월 예산 알림(`budgets.tf`): 실제 80%·100%, 예측 100% 초과 시 메일.
- 서버 프록시는 `X-Origin-Verify` 값이 SSM `/moamap/<env>/ORIGIN_VERIFY_SECRET`과 다르면 403.
- RDS는 prod 서버 SG에서만 5432.

## 계정 혼동 방지

로컬 프로필을 계정별로 나눈다. 이 디렉터리는 **항상 `AWS_PROFILE=moamap-b`**.
provider에 `allowed_account_ids`가 걸려 있어 다른 계정 자격증명이면 plan에서 거부된다.

```bash
export AWS_PROFILE=moamap-b
aws sts get-caller-identity   # Account가 B인지 확인
```

## 0. 부트스트랩 (최초 1회)

```bash
aws s3api create-bucket --bucket moamap-team-tfstate --region ap-northeast-2 \
  --create-bucket-configuration LocationConstraint=ap-northeast-2
aws s3api put-bucket-versioning --bucket moamap-team-tfstate --versioning-configuration Status=Enabled
aws s3api put-public-access-block --bucket moamap-team-tfstate \
  --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

버킷 이름이 이미 있으면(S3 이름은 전 세계 유일) `versions.tf`의 `bucket`을 바꾼다.

## 1. 인프라 생성

```bash
cp terraform.tfvars.example terraform.tfvars   # account_id, 버킷 이름 채우기 (커밋 금지)
terraform init

# ① 인증서만 먼저 — 검증 레코드 값을 얻기 위해
terraform apply -target=aws_acm_certificate.api
terraform output acm_validation_records
```

② Cloudflare DNS에 위 CNAME을 **프록시 끔(회색 구름)** 으로 추가한다. `api`·`api-dev` 레코드 자체는 아직 건드리지 않는다.

```bash
# ③ 전체. 인증서 발급(수 분)을 기다렸다가 CloudFront까지 만든다.
terraform apply
```

## 2. 시크릿 등록 (환경별, 전부 새 값)

Terraform이 넣는 것(`ssm.tf`): `ORIGIN_VERIFY_SECRET`, `JWT_SECRET`(환경마다 다름 → dev 토큰으로 prod 로그인 불가),
`RABBITMQ_PASSWORD`, `DB_PASSWORD`, `DB_URL`(prod), `OBJECT_STORAGE_BUCKET`, `OBJECT_STORAGE_PUBLIC_BASE_URL`.
비밀 아닌 공통 설정은 `deploy/config/*.env`(커밋됨).

외부 서비스 키만 CLI로 넣는다. 값이 코드·state에 남지 않는다. **값에 작은따옴표(')가 있으면 안 된다.**

```bash
ENV=dev   # prod도 반복
put() { aws ssm put-parameter --type SecureString --overwrite --name "/moamap/$ENV/$1" --value "$2"; }

# 애플 refresh token 암호화 키. A DB를 이전하는 prod는 A와 같은 값, dev는 새 값. 한 번 정하면 바꾸지 않는다.
put APPLE_CREDENTIAL_KEY_V1 '...'
put KAKAO_APP_ID '...'
put KAKAO_REST_API_KEY '...'
put GEMINI_API_KEY '...'
put GEMINI_MODEL_ID '...'
put SEOUL_API_KEY '...'
put RESTROOM_API_SERVICE_KEY '...'
aws ssm put-parameter --type SecureString --overwrite --name "/moamap/$ENV/APPLE_PRIVATE_KEY" --value file://AuthKey.p8
```

## 3. 배포 (`.github/workflows/cd-team.yml`)

GitHub → Settings → Secrets and variables → Actions → **Variables**에 `terraform output` 값을 넣는다.

| Variable | output |
|---|---|
| `TEAM_AWS_CI_ROLE_ARN` | `ci_role_arn` |
| `TEAM_ECR_REGISTRY` | `ecr_registry` |
| `TEAM_DEV_INSTANCE_ID` | `server_instance_ids.dev` |
| `TEAM_PROD_INSTANCE_ID` | `server_instance_ids.prod` |

Settings → Environments → **production** → Required reviewers 지정(main 머지 시 승인 후 배포).

develop push → dev, main push → prod. 수동 실행은 Actions → CD (team account) → Run workflow.
서버에서는 `/opt/moamap`에 `deploy/` 묶음이 풀리고 `deploy.sh`가 `.env` 생성 → pull → up → 헬스체크까지 한다.

```bash
aws ssm start-session --target <instance-id>
sudo docker compose -f /opt/moamap/docker-compose.yml ps
sudo docker compose -f /opt/moamap/docker-compose.yml logs -f user-service
```

prod RDS는 비어 있다. A DB를 옮기지 않는다면 서비스별 스키마부터 만든다(`docker/postgres/init/01-schemas.sql`).

## 4. 전환 전 검증 (DNS는 그대로)

```bash
terraform output api_cloudfront_domains
curl -i https://<dev 주소>.cloudfront.net/actuator/health
```

- 응답 헤더 `x-amz-cf-pop`이 `ICN…`인지
- API 응답에 `x-cache: Hit`이 **없는지**
- `curl http://<EIP>` 직접 접근이 **막히는지** (SG에서 타임아웃)

## 5. 전환 (앱 심사 통과 후)

Cloudflare에서 `api-dev` → 하루 확인 → `api` 순서로:
레코드를 **CNAME `<api_cloudfront_domains 값>`, 프록시 끔**으로 바꾼다. 되돌리려면 원래 레코드로 복구(몇 분).

## 주의

- **arm64(Graviton)**. 이미지는 `linux/arm64`로 빌드.
- `spot` 값만 바꾸면 plan이 변경을 감지하지 못한다(provider가 `instance_market_options`를 computed로 취급). 바꿀 때는 `terraform apply -replace='aws_instance.server["dev"]'` — 서버가 교체되므로 dev DB 컨테이너 데이터가 사라진다. 교체 후 `TEAM_DEV_INSTANCE_ID`도 갱신.
- prod 실데이터 이후 `db_deletion_protection = true`.
- 원본 구간은 HTTP다. HTTPS로 올릴 때는 `cloudfront.tf`의 `origin_protocol_policy` + 서버 프록시 인증서.
