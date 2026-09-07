# 노드에 붙는 인스턴스 프로파일. 정적 액세스 키 없이 S3·EBS에 접근한다.

resource "aws_iam_role" "node" {
  name = "${var.name_prefix}-node"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_instance_profile" "node" {
  name = "${var.name_prefix}-node"
  role = aws_iam_role.node.name
}

# EBS CSI 드라이버가 볼륨을 만들고 붙이는 데 필요한 권한.
resource "aws_iam_role_policy_attachment" "ebs_csi" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy"
}

# SSM Session Manager. 22를 열지 않고 베스천 없이 프라이빗 노드에 붙는다.
resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# ECR pull. imagePullSecret 없이 containerd가 IMDS 자격증명으로 이미지를 받는다.
resource "aws_iam_role_policy_attachment" "ecr_pull" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# 사진 버킷만. 다른 버킷은 손대지 못한다.
resource "aws_iam_role_policy" "photo_bucket" {
  name = "${var.name_prefix}-photo-bucket"
  role = aws_iam_role.node.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:ListBucket"]
        Resource = aws_s3_bucket.photo.arn
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = "${aws_s3_bucket.photo.arn}/*"
      }
    ]
  })
}
