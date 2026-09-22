# 인증 정보는 코드에 넣지 않는다.
# OpenStack openrc 환경변수(OS_AUTH_URL / OS_USERNAME / OS_PASSWORD / OS_TENANT_ID / OS_REGION_NAME)로 주입한다.
# → public 레포에 크레덴셜이 남지 않음. (openrc.sample 참고)
provider "nhncloud" {
  region = var.region
}
