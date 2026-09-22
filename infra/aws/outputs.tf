# 민감정보(k3s 토큰, kubeconfig)는 출력하지 않는다. DB 비밀번호만 sensitive로 꺼낸다.

output "ingress_eip" {
  description = "가비아 A 레코드가 가리킬 고정 IP (worker-1에 연결)."
  value       = aws_eip.ingress.public_ip
}

output "master_instance_id" {
  description = "SSM 접속 대상. aws ssm start-session --target <이 값>"
  value       = aws_instance.master.id
}

output "worker_instance_ids" {
  value = aws_instance.worker[*].id
}

output "db_endpoint" {
  description = "k8s Secret의 DB_HOST에 넣을 값."
  value       = aws_db_instance.main.address
}

output "db_password" {
  description = "k8s Secret용. terraform output -raw db_password 로 확인."
  value       = random_password.db.result
  sensitive   = true
}

output "photo_bucket" {
  value = aws_s3_bucket.photo.bucket
}

output "ecr_repository_urls" {
  description = "CI가 push할 리포지토리 URL."
  value       = { for k, r in aws_ecr_repository.service : k => r.repository_url }
}

output "ci_role_arn" {
  description = "GitHub Actions가 AssumeRole할 IAM Role ARN."
  value       = aws_iam_role.ci.arn
}
