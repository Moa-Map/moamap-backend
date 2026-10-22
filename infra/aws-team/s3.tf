# 사진 버킷. 비공개 — 읽기는 CloudFront(OAC)로만, 업로드는 앱이 presigned URL로 직접 PUT.
# 앱은 OBJECT_STORAGE_PUBLIC_BASE_URL(= 사진 CloudFront 주소) + 키로 조회 URL을 만든다.

resource "aws_s3_bucket" "photo" {
  for_each = var.envs
  bucket   = each.value.photo_bucket_name
}

resource "aws_s3_bucket_public_access_block" "photo" {
  for_each = var.envs

  bucket                  = aws_s3_bucket.photo[each.key].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_policy" "photo_cloudfront_read" {
  for_each = var.envs
  bucket   = aws_s3_bucket.photo[each.key].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "CloudFrontOACRead"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.photo[each.key].arn}/*"
      Condition = {
        StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.photo[each.key].arn }
      }
    }]
  })
}
