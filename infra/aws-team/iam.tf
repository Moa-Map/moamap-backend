# 환경별 서버 역할. dev 역할은 dev 버킷·dev 파라미터만 접근한다 — dev 사고가 prod로 번지지 않게.

resource "aws_iam_role" "server" {
  for_each = var.envs
  name     = "${var.name_prefix}-${each.key}-server"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_instance_profile" "server" {
  for_each = var.envs
  name     = "${var.name_prefix}-${each.key}-server"
  role     = aws_iam_role.server[each.key].name
}

# SSM Session Manager 접속 + CD의 Run Command 수신.
resource "aws_iam_role_policy_attachment" "server_ssm" {
  for_each   = var.envs
  role       = aws_iam_role.server[each.key].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "server_ecr_pull" {
  for_each   = var.envs
  role       = aws_iam_role.server[each.key].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_role_policy" "server" {
  for_each = var.envs
  name     = "${var.name_prefix}-${each.key}-server"
  role     = aws_iam_role.server[each.key].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:ListBucket"]
        Resource = aws_s3_bucket.photo[each.key].arn
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = "${aws_s3_bucket.photo[each.key].arn}/*"
      },
      # 시크릿은 /moamap/<env>/ 아래 SecureString. 기본 KMS 키(aws/ssm)라 kms 권한은 따로 필요 없다.
      {
        Effect   = "Allow"
        Action   = ["ssm:GetParametersByPath", "ssm:GetParameters", "ssm:GetParameter"]
        Resource = "arn:aws:ssm:${var.region}:${var.account_id}:parameter/${var.name_prefix}/${each.key}/*"
      }
    ]
  })
}
