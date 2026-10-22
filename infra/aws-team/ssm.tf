# 서버는 배포 때 /moamap/<env>/ 아래를 전부 읽어 .env로 만든다(deploy/deploy.sh).
# 여기서는 Terraform이 만들거나 아는 값만 넣는다. 외부 서비스 키(카카오·애플·Gemini 등)는
# README "2. 시크릿 등록"대로 CLI로 넣는다 — 값이 코드·state에 남지 않게.

resource "aws_ssm_parameter" "origin_verify" {
  for_each = var.envs

  name  = "/${var.name_prefix}/${each.key}/ORIGIN_VERIFY_SECRET"
  type  = "SecureString"
  value = random_password.origin_verify[each.key].result
}

resource "aws_ssm_parameter" "db_password_prod" {
  name  = "/${var.name_prefix}/prod/DB_PASSWORD"
  type  = "SecureString"
  value = random_password.db.result
}

# ---------------- 자동 생성 시크릿 (환경마다 다른 값) ----------------
# dev 토큰으로 prod에 로그인할 수 없게 JWT_SECRET을 환경별로 따로 만든다.
resource "random_password" "jwt" {
  for_each = var.envs
  length   = 64
  special  = false
}

resource "random_password" "rabbitmq" {
  for_each = var.envs
  length   = 32
  special  = false
}

resource "random_password" "db_dev" {
  length  = 32
  special = false
}

locals {
  generated_secrets = merge(
    { for env in keys(var.envs) : "${env}/JWT_SECRET" => random_password.jwt[env].result },
    { for env in keys(var.envs) : "${env}/RABBITMQ_PASSWORD" => random_password.rabbitmq[env].result },
    { "dev/DB_PASSWORD" = random_password.db_dev.result },
  )

  # 비밀은 아니지만 apply 후에야 알 수 있는 값. 손으로 옮겨 적지 않게 여기서 넣는다.
  generated_config = merge(
    { for env in keys(var.envs) : "${env}/OBJECT_STORAGE_BUCKET" => aws_s3_bucket.photo[env].bucket },
    { for env in keys(var.envs) : "${env}/OBJECT_STORAGE_PUBLIC_BASE_URL" => "https://${aws_cloudfront_distribution.photo[env].domain_name}" },
    { "prod/DB_URL" = "jdbc:postgresql://${aws_db_instance.main.address}:5432/${var.db_name}" },
  )
}

resource "aws_ssm_parameter" "generated_secret" {
  for_each = local.generated_secrets

  name  = "/${var.name_prefix}/${each.key}"
  type  = "SecureString"
  value = each.value
}

resource "aws_ssm_parameter" "generated_config" {
  for_each = local.generated_config

  name  = "/${var.name_prefix}/${each.key}"
  type  = "String"
  value = each.value
}
