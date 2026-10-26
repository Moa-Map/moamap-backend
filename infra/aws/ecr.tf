# 서비스별 리포지토리. 노드는 인스턴스 프로파일로, CI는 OIDC로 붙는다 — 저장된 비밀 없음.

resource "aws_ecr_repository" "service" {
  for_each = toset(var.services)

  name                 = "${var.name_prefix}/${each.value}"
  image_tag_mutability = "IMMUTABLE" # 커밋 SHA 태그 → 덮어쓸 일이 없다

  image_scanning_configuration {
    scan_on_push = true
  }
}

# 커밋 SHA 태그가 무한 누적되는 걸 막는다 (NHN NCR에서 겪은 문제).
resource "aws_ecr_lifecycle_policy" "service" {
  for_each   = aws_ecr_repository.service
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "최근 10개만 유지"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

# ---------------- CI(GitHub Actions) → ECR push ----------------
data "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 0 : 1
  url   = "https://token.actions.githubusercontent.com"
}

resource "aws_iam_openid_connect_provider" "github" {
  count          = var.create_github_oidc_provider ? 1 : 0
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  # thumbprint_list는 지정하지 않는다. AWS가 자체 검증으로 채우므로,
  # 값을 박아두면 plan마다 가짜 변경이 뜬다.
}

locals {
  github_oidc_arn = var.create_github_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : data.aws_iam_openid_connect_provider.github[0].arn
}

resource "aws_iam_role" "ci" {
  name = "${var.name_prefix}-ci-ecr-push"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = local.github_oidc_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = { "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com" }
        # 이 레포의 워크플로만 이 Role을 맡을 수 있다.
        StringLike = { "token.actions.githubusercontent.com:sub" = "repo:${var.github_repo}:*" }
      }
    }]
  })
}

resource "aws_iam_role_policy" "ci_ecr_push" {
  name = "${var.name_prefix}-ci-ecr-push"
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
        ]
        Resource = [for r in aws_ecr_repository.service : r.arn]
      }
    ]
  })
}
