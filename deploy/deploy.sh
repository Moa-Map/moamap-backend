#!/bin/bash
# 서버에서 실행된다(CD → SSM Run Command, root). 사용: deploy.sh <dev|prod> <image-tag> <ecr-registry>
# 1) .env 생성: config/common.env + config/<env>.env + SSM /moamap/<env>/* + 이미지 태그
# 2) ECR 로그인 → pull → up
# 3) CloudFront와 같은 경로(nginx → gateway)로 헬스체크
set -euo pipefail

ENV=$1
TAG=$2
REGISTRY=$3
REGION=ap-northeast-2

cd "$(dirname "$0")"
umask 077 # .env에 시크릿이 들어간다

{
  cat config/common.env "config/$ENV.env"
  echo "ECR_REGISTRY=$REGISTRY"
  echo "IMAGE_TAG=$TAG"
  # 작은따옴표로 감싸면 compose가 $를 치환하지 않고 줄바꿈(APPLE_PRIVATE_KEY)도 그대로 둔다.
  aws ssm get-parameters-by-path --region "$REGION" --path "/moamap/$ENV/" --with-decryption --output json |
    python3 -c '
import json, sys
for p in json.load(sys.stdin)["Parameters"]:
    key, value = p["Name"].rsplit("/", 1)[1], p["Value"]
    if "\x27" in value:
        sys.exit(f"{key}: 값에 작은따옴표가 있으면 .env에 넣을 수 없다")
    print(f"{key}=\x27{value}\x27")
'
} > .env.tmp
mv .env.tmp .env

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"
docker compose pull --quiet
docker compose up -d --remove-orphans
docker image prune -af --filter "until=168h" >/dev/null

SECRET=$(sed -n "s/^ORIGIN_VERIFY_SECRET='\(.*\)'$/\1/p" .env)
for _ in $(seq 1 36); do
  if curl -fsS -o /dev/null -H "X-Origin-Verify: $SECRET" http://localhost/actuator/health; then
    docker compose ps
    echo "배포 완료: $ENV $TAG"
    exit 0
  fi
  sleep 5
done

docker compose ps
docker compose logs --tail 50
echo "헬스체크 실패 (3분)" >&2
exit 1
