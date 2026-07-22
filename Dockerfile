# syntax=docker/dockerfile:1
# 멀티모듈 모노레포 공통 Dockerfile. SERVICE 빌드 인자만 바꿔 서비스별 이미지를 만든다.
#   docker build --build-arg SERVICE=gateway-service -t moamap/gateway-service:latest .
#   docker build --build-arg SERVICE=user-service    -t moamap/user-service:latest .
#   docker build --build-arg SERVICE=map-service     -t moamap/map-service:latest .
#   docker build --build-arg SERVICE=place-service   -t moamap/place-service:latest .

# ---- build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
ARG SERVICE
# 해당 서비스의 실행 가능한 bootJar만 빌드 (다른 모듈은 의존성으로 함께 컴파일됨).
RUN chmod +x gradlew && ./gradlew :${SERVICE}:bootJar --no-daemon

# ---- runtime stage ----
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
ARG SERVICE
# 보안: 루트가 아닌 사용자로 실행
RUN useradd -r -u 1001 appuser
COPY --from=build /workspace/${SERVICE}/build/libs/*.jar app.jar
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
