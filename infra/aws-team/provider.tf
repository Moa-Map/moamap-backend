# 자격증명은 코드에 넣지 않는다. AWS_PROFILE=moamap-b 로 실행.
# allowed_account_ids: A 계정 프로필로 실수로 실행하면 plan 단계에서 바로 거부된다.
provider "aws" {
  region              = var.region
  allowed_account_ids = [var.account_id]

  default_tags {
    tags = {
      Project   = var.name_prefix
      ManagedBy = "terraform"
    }
  }
}

# CloudFront용 ACM 인증서는 us-east-1에서만 발급된다.
provider "aws" {
  alias               = "us_east_1"
  region              = "us-east-1"
  allowed_account_ids = [var.account_id]

  default_tags {
    tags = {
      Project   = var.name_prefix
      ManagedBy = "terraform"
    }
  }
}
