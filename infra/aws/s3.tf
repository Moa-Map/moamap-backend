# 사진 업로드 버킷. 공개 읽기는 열지 않는다 — 조회는 presigned URL(S3Presigner)로 나간다.
resource "aws_s3_bucket" "photo" {
  bucket = var.photo_bucket_name
}

resource "aws_s3_bucket_public_access_block" "photo" {
  bucket                  = aws_s3_bucket.photo.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
