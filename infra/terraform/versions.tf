terraform {
  required_version = ">= 1.5"

  required_providers {
    nhncloud = {
      source  = "nhn-cloud/nhncloud"
      version = "~> 1.0"
    }
  }

  # 원격 state: NHN Object Storage(S3 호환)에 둔다. 버킷은 콘솔에서 미리 만들어야 함(Terraform 관리 밖).
  # 실제 endpoint/bucket/키는 backend.hcl로 주입: terraform init -backend-config=backend.hcl
  # (아래는 형태 예시이며, 값이 없으면 로컬 state로 동작한다.)
  # backend "s3" {
  #   bucket = "moamap-tfstate"
  #   key    = "infra/terraform.tfstate"
  #   # region/endpoints/자격증명은 backend.hcl 로 주입
  # }
}
