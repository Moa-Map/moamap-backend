# 서비스별 리포지토리(dev·prod 공용, 커밋 SHA 태그). 서버는 인스턴스 역할로, CI는 OIDC로 붙는다.

resource "aws_ecr_repository" "service" {
  for_each = toset(var.services)

  name                 = "${var.name_prefix}/${each.value}"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

# prod가 쓰는 이미지가 지워지지 않도록 dev보다 넉넉하게 남긴다.
resource "aws_ecr_lifecycle_policy" "service" {
  for_each   = aws_ecr_repository.service
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "최근 30개만 유지"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 30
      }
      action = { type = "expire" }
    }]
  })
}

# ---------------- CI(GitHub Actions) ----------------
# 새 계정이라 OIDC provider가 없다 → 항상 만든다.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

resource "aws_iam_role" "ci" {
  name = "${var.name_prefix}-ci"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = { "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com" }
        StringLike   = { "token.actions.githubusercontent.com:sub" = "repo:${var.github_repo}:*" }
      }
    }]
  })
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
        ]
        Resource = [for r in aws_ecr_repository.service : r.arn]
      },
      # 배포: 우리 서버 두 대에만 셸 명령을 보낼 수 있다.
      {
        Effect = "Allow"
        Action = "ssm:SendCommand"
        Resource = concat(
          [for i in aws_instance.server : i.arn],
          ["arn:aws:ssm:${var.region}::document/AWS-RunShellScript"]
        )
      },
      {
        Effect   = "Allow"
        Action   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
        Resource = "*"
      }
    ]
  })
}
