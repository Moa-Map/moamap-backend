# 앞단. DNS는 Cloudflare에 그대로 두고(웹이 Cloudflare Workers라 NS를 옮길 수 없다),
# api/api-dev 레코드만 프록시를 끄고 아래 API 배포 주소로 CNAME 한다.
#
#   앱 ──HTTPS(ACM)──▶ CloudFront ──HTTP + X-Origin-Verify──▶ EIP:80 (프록시 → gateway)
#
# ponytail: 원본 구간은 HTTP. 서버 인증서가 필요해지면 origin_protocol_policy = "https-only" +
# 서버 프록시 인증서(Cloudflare DNS-01)로 올린다. 앱·웹은 바뀌지 않는다.

data "aws_cloudfront_cache_policy" "disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_cache_policy" "optimized" {
  name = "Managed-CachingOptimized"
}

# Host만 빼고 헤더·쿼리·쿠키 전부 전달 — Origin(CORS), Authorization이 게이트웨이까지 간다.
data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  name = "Managed-AllViewerExceptHostHeader"
}

# ---------------- ACM (us-east-1) ----------------
# 검증 레코드는 Terraform이 만들 수 없다(DNS가 Cloudflare). output acm_validation_records 값을
# Cloudflare에 손으로 넣으면 aws_acm_certificate_validation이 발급 완료를 기다렸다가 통과한다.
resource "aws_acm_certificate" "api" {
  provider = aws.us_east_1
  for_each = var.envs

  domain_name       = each.value.api_domain
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_acm_certificate_validation" "api" {
  provider        = aws.us_east_1
  for_each        = var.envs
  certificate_arn = aws_acm_certificate.api[each.key].arn
}

# ---------------- API ----------------
# 원본 비밀 헤더 값. 같은 값이 SSM(/moamap/<env>/ORIGIN_VERIFY_SECRET)에도 들어가 서버 프록시가 비교한다.
resource "random_password" "origin_verify" {
  for_each = var.envs
  length   = 48
  special  = false
}

resource "aws_cloudfront_distribution" "api" {
  for_each = var.envs

  enabled     = true
  comment     = "${var.name_prefix}-${each.key}-api"
  aliases     = [each.value.api_domain]
  price_class = "PriceClass_200" # 한국 거점 포함 중 가장 싼 등급
  web_acl_id  = aws_wafv2_web_acl.api.arn

  origin {
    origin_id   = "server"
    domain_name = aws_eip.server[each.key].public_dns

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
      # 기본 30초. 가장 긴 외부 호출(Gemini 15초)보다 넉넉하다.
    }

    custom_header {
      name  = "X-Origin-Verify"
      value = random_password.origin_verify[each.key].result
    }
  }

  default_cache_behavior {
    target_origin_id       = "server"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    # 캐시를 켜면 한 사용자의 응답이 다른 사용자에게 보일 수 있다. API는 항상 끈다.
    cache_policy_id          = data.aws_cloudfront_cache_policy.disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.api[each.key].certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}

# ---------------- 사진 ----------------
# 자체 도메인 없이 *.cloudfront.net 기본 주소를 쓴다.
resource "aws_cloudfront_origin_access_control" "photo" {
  for_each = var.envs

  name                              = "${var.name_prefix}-${each.key}-photo"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "photo" {
  for_each = var.envs

  enabled     = true
  comment     = "${var.name_prefix}-${each.key}-photo"
  price_class = "PriceClass_200"

  origin {
    origin_id                = "photo"
    domain_name              = aws_s3_bucket.photo[each.key].bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.photo[each.key].id
  }

  default_cache_behavior {
    target_origin_id       = "photo"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true
    cache_policy_id        = data.aws_cloudfront_cache_policy.optimized.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}
