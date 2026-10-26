# prod 전용. dev는 서버 안 PostgreSQL 컨테이너를 쓴다(이 RDS에 네트워크로도 닿지 않는다).

resource "aws_db_subnet_group" "main" {
  name       = "${var.name_prefix}-db"
  subnet_ids = aws_subnet.private[*].id # 서브넷 그룹은 2AZ 필수 (인스턴스는 1AZ에 뜬다)
}

resource "random_password" "db" {
  length  = 32
  special = false # URL·환경변수에 그대로 넣어도 깨지지 않게
}

resource "aws_db_instance" "main" {
  identifier     = "${var.name_prefix}-prod-postgres"
  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  availability_zone      = var.azs[0]
  multi_az               = false
  publicly_accessible    = false

  backup_retention_period   = var.db_backup_retention_days
  deletion_protection       = var.db_deletion_protection
  skip_final_snapshot       = !var.db_deletion_protection
  final_snapshot_identifier = var.db_deletion_protection ? "${var.name_prefix}-prod-postgres-final" : null

  auto_minor_version_upgrade = true
  maintenance_window         = "mon:18:00-mon:19:00" # UTC = 월 03:00 KST
  backup_window              = "17:00-18:00"         # UTC = 02:00 KST
}
