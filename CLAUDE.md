# CLAUDE.md — Moa Map Backend

> 팀원과 Claude Code가 함께 보는 프로젝트 가이드.
> **코드만 봐서는 알 수 없는 것**만 적는다. 구조/규칙이 바뀌면 이 문서도 같이 고친다.

## 핵심 규칙 (먼저 읽을 것)

- **비밀정보(토큰/키/비밀번호) 커밋 금지.** 환경변수 또는 k8s Secret으로 관리. 예시값은 `.env.example`, `k8s/secrets/app-secrets.example.yaml`에 넣는다.
- **`main`/`develop`에 직접 push 금지.** 병합은 PR로만. PR 대상 브랜치는 `develop`.
- **gateway-service에 `spring-boot-starter-web` 추가 금지** — WebFlux 스택이라 서블릿 스타터가 들어오면 기동이 깨진다. 웹 기능이 필요하면 리액티브 대응물(`WebClient`, `spring-boot-starter-webflux`)을 쓴다.
- **Spring Cloud 버전은 Boot 릴리스 트레인에 맞춘다** (현재 Boot 3.5.x ↔ Cloud 2025.0.x). 한쪽만 올리면 기동이 깨진다.
- **의존성은 필요해지는 순간에만 추가한다.** 미리 넣어두지 않는다.
- **공통 응답/예외는 `common` 모듈 것을 쓴다** (`ApiResponse`, `BusinessException`, `ErrorCode`). 서비스마다 새로 만들지 않는다.
- ⚠️ **place-service ↔ map-service DTO 복사본**: place-service는 map-service의 멤버 역할 응답을 자체 record(`place/map/dto/MapMemberResponse`, `MapMemberRole`)로 역직렬화한다. 두 정의는 공유되지 않아 한쪽만 바뀌면 컴파일·테스트를 통과한 채 런타임에 깨진다. 둘 중 하나를 건드리면 반드시 양쪽을 함께 확인한다.

## 프로젝트 개요

**Moa Map(모아맵)** 은 여러 사용자가 함께 장소를 모으고 검증하는 커뮤니티 기반 지도 공유 플랫폼이다. 인스타그램 URL을 AI로 분석해 장소를 자동 등록하고, 서울 공공데이터(유동인구)로 혼잡도 지도를 제공한다.

지도 유형: **공식**(공공데이터 기반, 공개) / **커뮤니티**(사용자 생성, 퍼블릭) / **프라이빗**(초대 코드 참여, 비공개)

ERD: https://www.erdcloud.com/d/mhtzBRueYCraKZcxs

## 명령어

Gradle wrapper 사용 (로컬 Gradle 설치 불필요).

```bash
./gradlew build                  # 전체 빌드
./gradlew test                   # 전체 테스트
./gradlew :map-service:test      # 모듈 단위
./gradlew :user-service:bootRun  # 서비스 실행
curl localhost:8081/actuator/health
```

`common`은 라이브러리 모듈이라 `bootRun` 대상이 아니다.

## 기술 스택

| 구분 | 기술 |
|------|------|
| 언어/빌드 | Java 17 (toolchain 고정), Gradle 8.14.3 멀티모듈 |
| 프레임워크 | Spring Boot 3.5.6, Spring Cloud 2025.0.0 |
| 저장소 | PostgreSQL, Redis(user-service 리프레시 토큰), RabbitMQ(서비스 간 이벤트), S3(AWS SDK v2) |
| 문서/모니터링 | springdoc-openapi 2.7.0, Actuator + micrometer-prometheus |
| 배포 | Kubernetes(`k8s/`, kustomize overlays dev/prod) + ArgoCD, GitHub Actions(`.github/workflows/`) |

## 구조

```
moamap-backend/
├── common/               # 공통 응답/예외/DTO/유틸 (java-library, 실행 X)
├── gateway-service/      # 라우팅, JWT 검증 후 X-User-Id 전파, CORS
├── user-service/         # 사용자·인증(카카오 OAuth, JWT)
├── place-service/        # 장소 등록·리뷰, 카카오맵/Gemini 연동
├── map-service/          # 지도·멤버십·초대코드, 유동인구 수집
└── notification-service/ # 알림 (미착수 — 껍데기만)
```

| 서비스 | 포트 | 스택 |
|--------|------|------|
| gateway-service | 8080 | WebFlux (Spring Cloud Gateway) |
| user-service | 8081 | MVC |
| place-service | 8082 | MVC |
| map-service | 8083 | MVC |
| notification-service | 8084 | MVC |

패키지는 `com.moamap.<service>`. 인증이 필요한 요청은 게이트웨이가 JWT를 검증하고 `X-User-Id` 헤더로 전파하므로, 각 서비스는 이 헤더를 신뢰한다(직접 JWT 파싱 X).

## 개발 규칙

**브랜치**: 이슈 먼저, 그다음 브랜치. 항상 최신 `develop`에서 분기(`git switch develop && git pull --ff-only`).
형식: `<type>/#<이슈번호>/<영문-kebab-요약>` (예: `feat/#12/social-login`)

**커밋**: `<type>: <설명>` 한 줄. type: `feat` `fix` `refactor` `docs` `test` `chore` `perf` `ci`

**PR**: 제목 `[TYPE] 작업 내용을 합니다 (#이슈번호)`. `.github/PULL_REQUEST_TEMPLATE.md` 체크리스트(연결 이슈 `Closes #`, 테스트 결과, 민감정보 미포함) 준수.

**코드**: 입력은 컨트롤러 경계에서 Bean Validation으로 검증한다. 삭제는 소프트 삭제이므로 조회 시 삭제 필터를 빠뜨리지 않는다. 커밋 전 `git status`로 `build/`·`.idea/`·`.env`·`*.key`가 섞이지 않았는지 확인한다.
