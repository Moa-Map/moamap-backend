# 자격증명은 코드에 넣지 않는다. AWS_PROFILE 또는 환경변수로 주입.
provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.name_prefix
      ManagedBy = "terraform"
    }
  }
}
