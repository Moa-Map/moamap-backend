# 서비스별 리포지토리(dev·prod 공용, 커밋 SHA 태그). 서버는 인스턴스 역할로, CI는 OIDC로 붙는다.

resource "aws_ecr_repository" "service" {
  for_each = toset(var.services)

  name                 = "${var.name_prefix}/${each.value}"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

# main 빌드는 `prod-<sha>` 태그를 함께 단다(cd-team.yml). 상위 규칙에 걸린 이미지는 하위 규칙이 지우지 않으므로
# develop push가 많아도 prod 이미지(롤백용 최근 10개)는 남는다.
resource "aws_ecr_lifecycle_policy" "service" {
  for_each   = aws_ecr_repository.service
  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "prod 이미지 최근 10개 유지"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["prod-"]
          countType     = "imageCountMoreThan"
          countNumber   = 10
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "나머지 최근 30개 유지"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 30
        }
        action = { type = "expire" }
      }
    ]
  })
}

# ---------------- CI(GitHub Actions) ----------------
# 새 계정이라 OIDC provider가 없다 → 항상 만든다.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

# GitHub OIDC 토큰의 sub는 job에 environment가 있으면 `repo:<r>:environment:<name>`, 없으면 `repo:<r>:ref:<ref>`다.
# - ci 역할: 빌드(브랜치 push) + dev 배포(environment development). prod 서버에는 명령을 보낼 수 없다.
# - deploy_prod 역할: environment production(승인자·main 제한이 걸린 환경)에서만 맡을 수 있다.
# 역할을 하나로 두면 아무 브랜치의 워크플로가 승인 없이 prod에 root 명령을 보낼 수 있다.
locals {
  oidc_trust = {
    ci          = ["repo:${var.github_repo}:ref:refs/heads/*", "repo:${var.github_repo}:environment:development"]
    deploy_prod = ["repo:${var.github_repo}:environment:production"]
  }
  deploy_targets = {
    ci          = "dev"
    deploy_prod = "prod"
  }
}

resource "aws_iam_role" "ci" {
  name               = "${var.name_prefix}-ci"
  assume_role_policy = data.aws_iam_policy_document.github_trust["ci"].json
}

resource "aws_iam_role" "deploy_prod" {
  name               = "${var.name_prefix}-deploy-prod"
  assume_role_policy = data.aws_iam_policy_document.github_trust["deploy_prod"].json
}

data "aws_iam_policy_document" "github_trust" {
  for_each = local.oidc_trust

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = each.value
    }
  }
}

resource "aws_iam_role_policy" "ci" {
  name = "${var.name_prefix}-ci"
  role = aws_iam_role.ci.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
          "ecr:PutImage",
          "ecr:BatchGetImage",
          "ecr:DescribeImages", # 같은 SHA 재빌드 건너뛰기(IMMUTABLE 태그)
        ]
        Resource = [for r in aws_ecr_repository.service : r.arn]
      },
    ]
  })
}

# 배포: 각 역할은 자기 환경 서버 한 대에만 셸 명령을 보낼 수 있다.
resource "aws_iam_role_policy" "deploy" {
  for_each = local.deploy_targets

  name = "${var.name_prefix}-deploy-${each.value}"
  role = each.key == "ci" ? aws_iam_role.ci.id : aws_iam_role.deploy_prod.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = "ssm:SendCommand"
        Resource = [
          aws_instance.server[each.value].arn,
          "arn:aws:ssm:${var.region}::document/AWS-RunShellScript",
        ]
      },
      {
        Effect   = "Allow"
        Action   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
        Resource = "*"
      }
    ]
  })
}
