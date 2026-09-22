terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # 원격 state: S3. 버킷은 이 스택 밖에서 먼저 만든다(부트스트랩).
  #   aws s3api create-bucket --bucket moamap-tfstate --region ap-northeast-2 \
  #     --create-bucket-configuration LocationConstraint=ap-northeast-2
  #   aws s3api put-bucket-versioning --bucket moamap-tfstate --versioning-configuration Status=Enabled
  # state에는 k3s 조인 토큰이 들어간다 — 버킷은 반드시 비공개 + 암호화.
  # backend "s3" {
  #   bucket       = "moamap-tfstate"
  #   key          = "aws/terraform.tfstate"
  #   region       = "ap-northeast-2"
  #   encrypt      = true
  #   use_lockfile = true
  # }
}
