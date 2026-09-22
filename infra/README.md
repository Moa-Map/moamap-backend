# infra — 환경별 Terraform

| 디렉터리 | 대상 | 상태 |
|---|---|---|
| `nhncloud/` | NHN Cloud NKS (VPC, 서브넷, NKS 클러스터) | **이관 원본.** 정리(destroy)까지 유지 |
| `aws/` | AWS k3s (VPC, EC2×3, RDS, ECR, S3, IAM) | **이관 대상.** 신규 |

state는 완전히 분리한다. 서로의 리소스를 참조하지 않는다.

설계·이관 절차는 [`docs/infra/nhn-to-aws-k3s-migration.md`](../docs/infra/nhn-to-aws-k3s-migration.md).

## 이력

- 2026-09-07 `infra/terraform/` → `infra/nhncloud/` 이동 (파일 내용 변경 없음). AWS 스택과 나란히 두기 위해서다.
- NHN 리소스를 destroy한 뒤에도 `nhncloud/`는 지우지 않는다 — destroy에 필요하고, "이전 구성"이 마이그레이션 기록의 절반이다.
