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

  # 팀 계정(B) 전용 state. 이전 계정(A)의 moamap-tfstate와 절대 같은 이름을 쓰지 않는다.
  # 버킷은 이 스택 밖에서 먼저 만든다 — README "0. 부트스트랩" 참고.
  # state에 DB 비밀번호·원본 비밀 헤더가 들어간다 → 비공개 + 암호화 + 버전관리 필수.
  backend "s3" {
    bucket       = "moamap-team-tfstate"
    key          = "aws-team/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}
