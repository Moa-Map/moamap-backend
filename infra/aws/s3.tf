# 사진 업로드 버킷. 객체 읽기는 공개다 — 앱이 DB에 절대 URL(fileUrl)을 저장하고 그대로 내려준다.
resource "aws_s3_bucket" "photo" {
  bucket = var.photo_bucket_name
}

# 정책 기반 공개 읽기만 허용한다. ACL 경로는 계속 막아둔다(객체 소유권은 버킷에 고정).
# 목록(ListBucket)은 공개하지 않는다 — 파일명이 UUID라 목록을 못 얻으면 키를 추측할 수 없다.
resource "aws_s3_bucket_public_access_block" "photo" {
  bucket                  = aws_s3_bucket.photo.id
  block_public_acls       = true
  block_public_policy     = false
  ignore_public_acls      = true
  restrict_public_buckets = false
}

# ⚠️ 이 버킷에 올라가는 사진은 전부 URL만 알면 누구나 열람할 수 있다.
# prefix로 공개 범위를 나눌 수 없어서(키의 mapId만으로는 그 지도가 PRIVATE인지 알 수 없다)
# 전체 공개를 택했다. 프라이빗 지도 사진도 여기 섞여 있다 — 접근 제어가 필요해지면
# 조회 시점 presigned GET으로 바꿔야 하고, 그때 DB의 절대 URL 저장 구조도 함께 바뀐다.
resource "aws_s3_bucket_policy" "photo_public_read" {
  bucket = aws_s3_bucket.photo.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "PublicReadGetObject"
      Effect    = "Allow"
      Principal = "*"
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.photo.arn}/*"
    }]
  })

  # public access block이 먼저 풀려야 공개 정책을 붙일 수 있다.
  depends_on = [aws_s3_bucket_public_access_block.photo]
}
