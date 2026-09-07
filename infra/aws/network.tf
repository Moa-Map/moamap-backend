# public 2AZ: 노드(공인 IP 보유, IGW로 직접 아웃바운드) — NAT 없음.
# private 2AZ: RDS 전용. 아웃바운드가 필요 없어서 인터넷 경로를 두지 않는다.
# 노드는 EBS 때문에 azs[0] 한 곳에만 둔다. 2AZ가 필요한 건 RDS 서브넷 그룹뿐.

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

# ---------------- 라우팅 ----------------
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

# RDS만 있는 서브넷. 기본 라우트를 두지 않아 인터넷과 단절된다(local만).
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${var.name_prefix}-private-rt" }
}

resource "aws_route_table_association" "private" {
  count          = length(aws_subnet.private)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# S3 게이트웨이 엔드포인트는 무료다. 사진·etcd 스냅샷 트래픽이 인터넷을 타지 않는다.
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.public.id]
}

# ---------------- 보안 그룹 ----------------
# 모든 노드에 붙는다. 인바운드는 노드끼리(자기참조)뿐 — 마스터는 이것만 붙어서
# 인터넷에서 열린 포트가 하나도 없다.
resource "aws_security_group" "node" {
  name        = "${var.name_prefix}-node"
  description = "k3s nodes"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${var.name_prefix}-node" }
}

# 6443(API)/2379-2380(etcd)/10250(kubelet)/8472udp(flannel)를 개별 나열하는 대신 자기참조 한 줄.
resource "aws_vpc_security_group_ingress_rule" "node_self" {
  security_group_id            = aws_security_group.node.id
  referenced_security_group_id = aws_security_group.node.id
  ip_protocol                  = "-1"
  description                  = "cluster internal"
}

resource "aws_vpc_security_group_egress_rule" "node_all" {
  security_group_id = aws_security_group.node.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# 워커에만 추가로 붙는다. 80은 서비스 트래픽 + Let's Encrypt HTTP-01 챌린지용.
resource "aws_security_group" "ingress" {
  name        = "${var.name_prefix}-ingress"
  description = "traefik 80/443"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${var.name_prefix}-ingress" }
}

resource "aws_vpc_security_group_ingress_rule" "traefik" {
  for_each = toset(["80", "443"])

  security_group_id = aws_security_group.ingress.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = each.value
  to_port           = each.value
  description       = "traefik"
}

resource "aws_security_group" "rds" {
  name        = "${var.name_prefix}-rds"
  description = "RDS postgres"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${var.name_prefix}-rds" }
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_node" {
  security_group_id            = aws_security_group.rds.id
  referenced_security_group_id = aws_security_group.node.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "k3s pods to postgres"
}
