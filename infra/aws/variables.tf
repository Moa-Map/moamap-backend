variable "region" {
  description = "AWS 리전."
  type        = string
  default     = "ap-northeast-2"
}

variable "azs" {
  description = "AZ 2개. ALB와 RDS 서브넷 그룹이 2AZ를 요구한다. 노드와 RDS 인스턴스는 azs[0]에만 뜬다."
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]
}

variable "name_prefix" {
  description = "리소스 이름 접두사."
  type        = string
  default     = "moamap"
}

# ---------------- 네트워크 ----------------
variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

# 서브넷 대역은 vpc_cidr에서 자동 계산한다: public 10.0.1.0/24, 10.0.2.0/24 / private 10.0.11.0/24, 10.0.12.0/24
# SSH 키페어는 두지 않는다 — 접속은 SSM Session Manager로만.

# ---------------- 컴퓨트 ----------------
variable "master_instance_type" {
  description = "k3s server 노드. taint로 워크로드를 받지 않으므로 작게 잡는다."
  type        = string
  default     = "t4g.small"
}

variable "worker_instance_type" {
  description = "k3s agent 노드. 모든 워크로드가 여기 올라간다."
  type        = string
  default     = "t4g.medium"
}

variable "worker_count" {
  description = "워커 노드 개수. (과금 직결)"
  type        = number
  default     = 2
}

variable "master_root_volume_size" {
  type    = number
  default = 20
}

variable "worker_root_volume_size" {
  type    = number
  default = 30
}

variable "k3s_version" {
  description = "설치할 k3s 버전(INSTALL_K3S_VERSION). 빈 값이면 stable 채널 최신."
  type        = string
  default     = ""
}

# ---------------- 진입점 ----------------
# 도메인은 Terraform이 쓰지 않는다(가비아 A 레코드 → EIP는 수동, TLS는 traefik이 발급).
# 인증서는 traefik 내장 ACME(Let's Encrypt)가 처리한다 — k8s/cluster/traefik-config.yaml 참고.

# ---------------- RDS ----------------
variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_engine_version" {
  description = "메이저 버전만 적으면 그 시점의 최신 마이너로 붙는다(auto_minor_version_upgrade와 짝). 특정 마이너를 고정하려면 \"16.15\"처럼 적는다."
  type        = string
  default     = "16"
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
  description = "스토리지 오토스케일 상한(GB). 예상 밖 증가에 대한 안전장치."
  type        = number
  default     = 50
}

variable "db_backup_retention_days" {
  type    = number
  default = 7
}

variable "db_deletion_protection" {
  description = "실서비스 데이터가 들어간 뒤에는 true. false면 destroy 시 최종 스냅샷도 남기지 않는다."
  type        = bool
  default     = false
}

# ---------------- 스토리지 ----------------
variable "photo_bucket_name" {
  description = "사진 업로드 S3 버킷 이름(전역 유일)."
  type        = string
}

# ---------------- 컨테이너 레지스트리 ----------------
variable "services" {
  description = "ECR 리포지토리를 만들 서비스 목록."
  type        = list(string)
  default     = ["gateway-service", "user-service", "place-service", "map-service"]
}

variable "github_repo" {
  description = "CI가 이 Role을 맡을 수 있는 GitHub 레포 (owner/repo)."
  type        = string
  default     = "Moa-Map/moamap-backend"
}

variable "create_github_oidc_provider" {
  description = "계정에 GitHub OIDC provider가 아직 없으면 true. 이미 있으면 false(기존 것을 참조)."
  type        = bool
  default     = true
}
