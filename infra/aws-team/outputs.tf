output "acm_validation_records" {
  description = "Cloudflare에 추가할 ACM 검증 CNAME (프록시 끔). 추가해야 apply가 끝난다."
  value = {
    for env, c in aws_acm_certificate.api : env => [
      for o in c.domain_validation_options : {
        name  = o.resource_record_name
        type  = o.resource_record_type
        value = o.resource_record_value
      }
    ]
  }
}

output "api_cloudfront_domains" {
  description = "전환 시 Cloudflare에서 api/api-dev를 이 주소로 CNAME (프록시 끔). 전환 전 검증도 이 주소로."
  value       = { for env, d in aws_cloudfront_distribution.api : env => d.domain_name }
}

output "photo_base_urls" {
  description = "OBJECT_STORAGE_PUBLIC_BASE_URL 값."
  value       = { for env, d in aws_cloudfront_distribution.photo : env => "https://${d.domain_name}" }
}

output "photo_buckets" {
  description = "OBJECT_STORAGE_BUCKET 값."
  value       = { for env, b in aws_s3_bucket.photo : env => b.bucket }
}

output "server_instance_ids" {
  description = "SSM 접속·배포 대상. aws ssm start-session --target <값>"
  value       = { for env, i in aws_instance.server : env => i.id }
}

output "db_endpoint" {
  description = "prod DB_URL 호스트."
  value       = aws_db_instance.main.address
}

output "ecr_registry" {
  value = split("/", aws_ecr_repository.service["gateway-service"].repository_url)[0]
}

output "ci_role_arn" {
  description = "GitHub Variables AWS_CI_ROLE_ARN에 넣을 값."
  value       = aws_iam_role.ci.arn
}
