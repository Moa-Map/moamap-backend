# public 2AZ: EC2(공인 IP, IGW로 직접 아웃바운드) — NAT 없음.
# private 2AZ: RDS 전용. 인터넷 경로 없음.

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true

  tags = { Name = "${var.name_prefix}-vpc" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${var.name_prefix}-igw" }
}

resource "aws_subnet" "public" {
  count = length(var.azs)

  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index + 1) # 10.0.1.0/24, 10.0.2.0/24
  availability_zone       = var.azs[count.index]
  map_public_ip_on_launch = true

  tags = { Name = "${var.name_prefix}-public-${var.azs[count.index]}" }
}

resource "aws_subnet" "private" {
  count = length(var.azs)

  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 11) # 10.0.11.0/24, 10.0.12.0/24
  availability_zone = var.azs[count.index]

  tags = { Name = "${var.name_prefix}-private-${var.azs[count.index]}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${var.name_prefix}-public-rt" }
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${var.name_prefix}-private-rt" }
}

resource "aws_route_table_association" "private" {
  count          = length(aws_subnet.private)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# 무료. 서버 ↔ S3 트래픽이 인터넷을 타지 않는다.
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.public.id]
}

# ---------------- 보안 그룹 ----------------
# AWS가 관리하는 CloudFront 원본 요청 IP 목록. 서버는 이 목록에서 오는 80만 받는다.
# 이 목록은 모든 AWS 고객의 CloudFront가 공유한다 → 우리 배포인지는 프록시가 비밀 헤더로 한 번 더 확인한다.
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

# 환경별로 나눈다 — RDS를 prod 서버에만 열기 위해.
resource "aws_security_group" "server" {
  for_each = var.envs

  name        = "${var.name_prefix}-${each.key}-server"
  description = "${each.key} server: CloudFront only"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${var.name_prefix}-${each.key}-server" }
}

resource "aws_vpc_security_group_ingress_rule" "server_from_cloudfront" {
  for_each = var.envs

  security_group_id = aws_security_group.server[each.key].id
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront.id
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  description       = "CloudFront origin-facing"
}

resource "aws_vpc_security_group_egress_rule" "server_all" {
  for_each = var.envs

  security_group_id = aws_security_group.server[each.key].id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_security_group" "rds" {
  name        = "${var.name_prefix}-rds"
  description = "RDS postgres: prod server only"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${var.name_prefix}-rds" }
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_prod" {
  security_group_id            = aws_security_group.rds.id
  referenced_security_group_id = aws_security_group.server["prod"].id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "prod server to postgres"
}
