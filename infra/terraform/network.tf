# VPC → 라우팅테이블 → 서브넷. NKS 클러스터가 이 위에 올라간다.

resource "nhncloud_networking_vpc_v2" "main" {
  name   = var.vpc_name
  cidrv4 = var.vpc_cidr
}

resource "nhncloud_networking_routingtable_v2" "main" {
  name        = "${var.vpc_name}-rt"
  vpc_id      = nhncloud_networking_vpc_v2.main.id
  distributed = false
}

resource "nhncloud_networking_vpcsubnet_v2" "main" {
  name            = var.subnet_name
  vpc_id          = nhncloud_networking_vpc_v2.main.id
  cidr            = var.subnet_cidr
  routingtable_id = nhncloud_networking_routingtable_v2.main.id
}

# 인터넷 게이트웨이 자체는 provider 미지원 — 콘솔에서 만들고 ID만 받아 라우팅테이블에 연결한다.
resource "nhncloud_networking_routingtable_attach_gateway_v2" "main" {
  routingtable_id = nhncloud_networking_routingtable_v2.main.id
  gateway_id      = var.gateway_id
}
