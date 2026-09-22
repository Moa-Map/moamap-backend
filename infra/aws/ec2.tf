# 컨트롤플레인 1대(server, taint) + 워커 N대(agent). 퍼블릭 서브넷 — IGW로 직접 아웃바운드(NAT 없음).
# 공인 IP는 있지만 마스터는 인바운드 규칙이 자기참조뿐이라 인터넷에서 열린 포트가 없다.
# 접속은 SSM Session Manager로 한다(22 미개방, 베스천 불필요).
#   aws ssm start-session --target <instance-id>
#   kubectl은 6443 포트포워딩: aws ssm start-session --target <master> \
#     --document-name AWS-StartPortForwardingSession --parameters portNumber=6443,localPortNumber=6443
#
# 조인 토큰은 여기서 만들어 양쪽 user_data에 넣는다 — 수동 복사 단계가 없다.
# (토큰이 state에 남으므로 state 버킷은 비공개·암호화 필수.)
resource "random_password" "k3s_token" {
  length  = 48
  special = false
}

data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

resource "aws_instance" "master" {
  ami                    = data.aws_ssm_parameter.al2023_arm64.value
  instance_type          = var.master_instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.node.id]
  iam_instance_profile   = aws_iam_instance_profile.node.name

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = var.master_root_volume_size
    encrypted   = true
  }

  # --cluster-init: 지금은 server 1대지만 embedded etcd로 시작해야 나중에 server를 붙일 수 있다.
  # --node-taint: 컨트롤플레인에는 워크로드를 띄우지 않는다.
  # apiserver는 VPC 밖으로 안 나가므로 tls-san이 따로 필요 없다(SSM 포트포워딩은 127.0.0.1).
  user_data = <<-EOT
    #!/bin/bash
    set -euo pipefail
    curl -sfL https://get.k3s.io | \
      INSTALL_K3S_VERSION="${var.k3s_version}" \
      K3S_TOKEN="${random_password.k3s_token.result}" \
      sh -s - server \
        --cluster-init \
        --node-taint node-role.kubernetes.io/control-plane=true:NoSchedule
  EOT

  tags = { Name = "${var.name_prefix}-master" }
}

resource "aws_instance" "worker" {
  count = var.worker_count

  ami                    = data.aws_ssm_parameter.al2023_arm64.value
  instance_type          = var.worker_instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.node.id, aws_security_group.ingress.id]
  iam_instance_profile   = aws_iam_instance_profile.node.name

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = var.worker_root_volume_size
    encrypted   = true
  }

  # 마스터가 아직 안 떠 있어도 k3s-agent 서비스가 계속 재시도하므로 대기 루프는 두지 않는다.
  user_data = <<-EOT
    #!/bin/bash
    set -euo pipefail
    curl -sfL https://get.k3s.io | \
      INSTALL_K3S_VERSION="${var.k3s_version}" \
      K3S_TOKEN="${random_password.k3s_token.result}" \
      K3S_URL="https://${aws_instance.master.private_ip}:6443" \
      sh -s - agent
  EOT

  tags = { Name = "${var.name_prefix}-worker-${count.index + 1}" }
}

# 서비스 진입점. DNS A 레코드가 가리키는 고정 IP.
# worker-1이 죽으면 이 EIP를 다른 워커로 옮긴다(association의 instance_id 수정 후 apply).
# 자동 페일오버는 없다 — ALB를 안 쓰기로 한 대가다.
resource "aws_eip" "ingress" {
  domain = "vpc"
  tags   = { Name = "${var.name_prefix}-ingress" }
}

resource "aws_eip_association" "ingress" {
  allocation_id = aws_eip.ingress.id
  instance_id   = aws_instance.worker[0].id
}
