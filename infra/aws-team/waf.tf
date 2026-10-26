# L7(HTTP 요청 폭탄) 방어. Shield Standard는 L3/L4만 막아 준다.
# CloudFront용 WAF는 us-east-1에 만든다. dev·prod API가 하나를 같이 쓴다(웹 ACL $5 + 규칙 $1).
# ponytail: IP당 5분 1,000회 단일 규칙. 오탐·공격 패턴이 보이면 관리형 규칙 그룹을 추가한다.

resource "aws_wafv2_web_acl" "api" {
  provider = aws.us_east_1

  name  = "${var.name_prefix}-api"
  scope = "CLOUDFRONT"

  default_action {
    allow {}
  }

  rule {
    name     = "rate-limit-per-ip"
    priority = 1

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit                 = var.waf_rate_limit_per_5min
        evaluation_window_sec = 300
        aggregate_key_type    = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name_prefix}-rate-limit"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.name_prefix}-api"
    sampled_requests_enabled   = true
  }
}
