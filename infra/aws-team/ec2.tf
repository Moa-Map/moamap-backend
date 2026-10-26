# 환경별 서버 1대 + Docker Compose. 22는 열지 않는다 — 접속은 SSM Session Manager.
#   aws ssm start-session --target <instance-id>
# 앱 배포(compose pull/up)는 CD가 SSM Run Command로 한다. 여기서는 Docker 설치까지만.

data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

resource "aws_instance" "server" {
  for_each = var.envs

  ami                    = data.aws_ssm_parameter.al2023_arm64.value
  instance_type          = each.value.instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.server[each.key].id]
  iam_instance_profile   = aws_iam_instance_profile.server[each.key].name

  # 스팟 회수 시 삭제가 아니라 정지 → 자원이 생기면 같은 디스크·EIP로 자동 재시작.
  dynamic "instance_market_options" {
    for_each = each.value.spot ? [1] : []
    content {
      market_type = "spot"
      spot_options {
        spot_instance_type             = "persistent"
        instance_interruption_behavior = "stop"
      }
    }
  }

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = each.value.root_volume_size
    encrypted   = true
  }

  # 컨테이너 로그 크기 제한(디스크 가득 참 방지), 스왑 2GB(메모리 여유 4GB 중 약 3GB 사용 예상).
  user_data = <<-EOT
    #!/bin/bash
    set -euo pipefail

    mkdir -p /etc/docker
    echo '{"log-driver":"json-file","log-opts":{"max-size":"10m","max-file":"3"}}' > /etc/docker/daemon.json
    dnf install -y docker
    systemctl enable --now docker

    mkdir -p /usr/local/lib/docker/cli-plugins
    curl -fsSL https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-aarch64 \
      -o /usr/local/lib/docker/cli-plugins/docker-compose
    chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

    dd if=/dev/zero of=/swapfile bs=1M count=2048
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab

    mkdir -p /opt/moamap
  EOT

  tags = { Name = "${var.name_prefix}-${each.key}", Env = each.key }

  # 새 AMI가 나와도 기존 서버를 교체하지 않는다.
  lifecycle {
    ignore_changes = [ami]
  }
}

# CloudFront 원본은 IP가 아니라 도메인이어야 한다 → EIP의 public_dns를 원본으로 쓴다.
# 서버를 재시작해도 IP·DNS가 그대로라 CloudFront 설정을 건드릴 필요가 없다.
resource "aws_eip" "server" {
  for_each = var.envs

  domain   = "vpc"
  instance = aws_instance.server[each.key].id
  tags     = { Name = "${var.name_prefix}-${each.key}" }
}
