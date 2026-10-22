variable "account_id" {
  description = "팀 계정(B) ID 12자리. 다른 계정 자격증명으로 실행되는 것을 막는다."
  type        = string
}

variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "azs" {
  description = "RDS 서브넷 그룹이 2AZ를 요구한다. EC2·RDS 인스턴스는 azs[0]에만 뜬다."
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]
}

variable "name_prefix" {
  type    = string
  default = "moamap"
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

# ---------------- 환경 ----------------
# 환경별 서버 1대. dev는 스팟(회수 시 정지 → 자원이 생기면 자동 재시작).
variable "envs" {
  type = map(object({
    instance_type     = string
    spot              = bool
    root_volume_size  = number
    api_domain        = string # 앱이 호출하는 도메인. Cloudflare에서 CloudFront로 CNAME
    photo_bucket_name = string # 전역 유일
  }))
}

# ---------------- RDS (prod 전용) ----------------
variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_engine_version" {
  type    = string
  default = "16"
}

variable "db_name" {
  type    = string
  default = "moamap"
}

variable "db_username" {
  type    = string
  default = "moamap"
}

variable "db_allocated_storage" {
  type    = number
  default = 20
}

variable "db_max_allocated_storage" {
  type    = number
  default = 50
}

variable "db_backup_retention_days" {
  type    = number
  default = 7
}

variable "db_deletion_protection" {
  description = "실데이터가 들어간 뒤에는 true."
  type        = bool
  default     = false
}

# ---------------- 비용 알림 ----------------
variable "monthly_budget_usd" {
  description = "월 예산(USD, 부가세 포함 청구액 기준). 예상 약 $91."
  type        = number
  default     = 100
}

variable "budget_alert_emails" {
  description = "예산 알림 받을 메일."
  type        = list(string)
}

# ---------------- WAF ----------------
variable "waf_rate_limit_per_5min" {
  description = "IP 하나가 5분 동안 보낼 수 있는 API 요청 수. 넘으면 차단(자동 해제)."
  type        = number
  default     = 1000
}

# ---------------- 컨테이너 레지스트리 · CI ----------------
variable "services" {
  type    = list(string)
  default = ["gateway-service", "user-service", "place-service", "map-service", "notification-service"]
}

variable "github_repo" {
  type    = string
  default = "Moa-Map/moamap-backend"
}
