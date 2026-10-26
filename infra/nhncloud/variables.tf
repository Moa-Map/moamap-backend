# 모든 값은 비밀이 아닌 환경 식별자/설정값. 실제 인증정보는 openrc 환경변수로만 주입한다.

variable "region" {
  description = "NHN Cloud 리전 (예: KR1)."
  type        = string
}

# ---------------- 네트워크 ----------------
variable "vpc_name" {
  description = "VPC 이름."
  type        = string
  default     = "moamap-vpc"
}

variable "vpc_cidr" {
  description = "VPC IP 대역."
  type        = string
  default     = "10.0.0.0/16"
}

variable "subnet_name" {
  description = "서브넷 이름."
  type        = string
  default     = "moamap-subnet"
}

variable "subnet_cidr" {
  description = "서브넷 IP 대역."
  type        = string
  default     = "10.0.1.0/24"
}

variable "gateway_id" {
  description = "콘솔에서 생성한 Internet Gateway UUID."
  type        = string
}

# ---------------- NKS 클러스터 ----------------
variable "cluster_name" {
  description = "NKS 클러스터 이름."
  type        = string
  default     = "moamap-nks"
}

variable "kube_tag" {
  description = "쿠버네티스 버전 태그 (예: v1.32.3). 콘솔에서 지원 버전 확인."
  type        = string
}

variable "availability_zone" {
  description = "가용 영역 (예: kr-pub-a)."
  type        = string
}

variable "node_flavor_name" {
  description = "워커 노드 사양 이름(ID가 아니라 이름). 기본은 최소 권장 2vCPU/4GB. 다른 사양은 openstack flavor list로 확인."
  type        = string
  default     = "u2.c2m4"
}

variable "node_image_name" {
  description = "워커 노드 이미지 이름. NKS 노드그룹 생성 화면/openstack image list의 이미지 '이름'을 그대로."
  type        = string
}

variable "keypair_name" {
  description = "노드 접속용 keypair 이름 (사전 생성 필요)."
  type        = string
}

variable "external_network_id" {
  description = "공인 IP를 할당받을 외부 네트워크 ID(Public Network). 라우팅테이블 조회의 external_network_id로 확인."
  type        = string
}

variable "external_subnet_id" {
  description = "외부 네트워크의 서브넷 ID. 여러 개를 넣을 때는 콜론(:)으로 구분."
  type        = string
}

variable "node_count" {
  description = "워커 노드 개수. (과금 직결 — 작게 시작)"
  type        = number
  default     = 2
}

variable "boot_volume_size" {
  description = "노드 부트 볼륨 크기(GB)."
  type        = string
  default     = "50"
}

variable "boot_volume_type" {
  description = "노드 부트 볼륨 타입."
  type        = string
  default     = "General HDD"
}
