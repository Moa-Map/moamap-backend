# k6 부하 테스트 — 성능 베이스라인 (#85)

> **상태: 통합 완료, 실행 검증 전** — 팀원 리뷰·수정 환영합니다.
> `chore/성능수치화`(측정 환경·시드·Prometheus)와 `chore/#85`(설계·시나리오)를 한 트리로 합쳤습니다.
> 부하량 수치는 측정 후 확정합니다.

## 디렉터리

부하 테스트에 관한 건 전부 `load-test/` 안에 있습니다. 밖에 흩어두지 않습니다.

```
load-test/
├── README.md                  이 문서
├── .env.loadtest.example      환경변수 양식 (.env.loadtest 는 gitignore)
├── compose.loadtest.yml       Prometheus + Grafana (부하 측정 전용 오버레이)
├── prometheus/prometheus.yml  스크랩 설정
├── grafana/provisioning/      데이터소스 자동 등록
├── seed/seed.sql              대량 시드 (재실행 안전)
└── scripts/
    ├── lib/
    │   ├── config.js          경로·시드 규모·멤버십 산식·부하 모양
    │   ├── auth.js            JWT 서명(gateway) / X-User-Id(direct)
    │   └── checks.js          응답 검증 (상태코드 + 데이터 유무)
    ├── scenarios/             자동 스위트
    │   ├── s1-home.js
    │   ├── s2-map-detail.js
    │   └── s5-mixed.js
    └── manual/                수동 전용 (과금·정합성)
        └── instagram-extract.js
```

**단일 출처 규칙**: 시드 규모와 고정 id는 `seed/seed.sql`의 `\set`과 `lib/config.js`의 `SEED`
**두 곳에만** 있고 서로 일치해야 합니다. 시나리오 스크립트에 숫자를 직접 박지 않습니다.

---

## 1. 왜 하는가

앞으로 리팩터링과 신규 기술 적용을 진행할 예정인데, **개선 효과를 판단할 기준 수치가 없습니다.**
지금은 "빨라진 것 같다" 수준의 판단만 가능하고, 무엇을 먼저 고쳐야 할지도 근거가 없습니다.

그래서 **리팩터링 이전 시점의 성능을 측정해두고, 이후 같은 조건으로 다시 재서 비교**하려고 합니다.

```
지금 (develop)          리팩터링              나중
    │                      │                    │
    ├─ 베이스라인 측정 ────────────────────────→ 동일 조건 재측정
    │                                            │
    └────────────── before / after 비교 ─────────┘
```

이 때문에 이 작업의 성공 조건은 "정확한 절대 성능"이 아니라 **"몇 달 뒤에 똑같은 조건을 재현할 수 있는가"** 입니다.
그래서 환경·시드·스크립트·부하 모델을 전부 코드로 고정해 레포에 넣습니다.

---

## 2. ⚠️ 시작 전 필독 — 하면 안 되는 것

부하 테스트는 **같은 요청을 수만 번** 보냅니다. 아래 엔드포인트는 **절대 시나리오에 넣지 마세요.**

| 엔드포인트 | 사유 |
|---|---|
| `POST /api/v1/places/instagram-extractions` | **Gemini API 호출당 과금** |
| `POST /api/v1/places/map-share-extractions` | **Gemini + Kakao Local API** |
| `POST /api/v1/auth/kakao/login` | 카카오 레이트리밋 / 계정 차단 위험 |
| `*/photo-upload-url` 계열 | 스토리지 미설정 시 503만 반환 → 측정 의미 없음 |

**실행 환경도 로컬 Docker로 한정합니다. NKS 클러스터에서 돌리지 마세요.**
- LB 트래픽 과금이 발생합니다
- 노드 2개짜리 공유 클러스터라 **팀 dev 환경이 마비됩니다**

> 서울시 열린데이터 API(`SeoulCityDataClient`)는 스케줄러에서만 호출되고 조회 API는 DB를 읽으므로 안전합니다.
> 다만 5분 주기 스케줄러가 측정에 노이즈를 주므로 `loadtest` 프로파일에서는 끕니다.

---

## 3. 무엇을 측정하는가 — 검증할 가설 4개

"느릴 것 같은 곳"이 아니라 **리팩터링으로 바뀔 곳**을 측정합니다. 코드를 읽고 뽑은 의심 지점입니다.

### ① 서비스 간 동기 호출 (가장 클 것으로 예상)

`PlaceService`의 조회/변경 **9곳**이 매번 map-service를 호출합니다. 캐시가 없습니다.

```
GET /api/v1/places  →  place-service  ──HTTP──→  map-service  →  DB
                                      (getMemberInfo)
```

요청 1건에 **네트워크 홉 1개 + DB 조회 1개**가 추가로 붙습니다.
→ 나중에 캐싱이나 인가 방식을 바꾸면 여기서 가장 큰 차이가 날 것으로 예상됩니다.

### ② 지도 목록의 tags N+1

`MapSummaryResponse`가 `tags`를 담는데, `MapEntity.tags`는 `@ElementCollection`(지연 로딩)입니다.
20개짜리 페이지를 내리면 **지도 조회 1 + 태그 조회 20 = 21쿼리**가 예상됩니다.

→ `@BatchSize`나 fetch join 한 줄로 21→2가 될 수 있는지, 실측으로 확인합니다.

### ③ DB 커넥션

HikariCP 설정이 없어 **서비스당 기본 10개**이고, 단일 PostgreSQL을 4개 서비스가 스키마로 나눠 씁니다.

→ 부하가 올라갈 때 커넥션 대기가 병목인지 확인합니다.

### ④ 장애 전파

서비스 간 HTTP 타임아웃이 **connect 2s / read 3s** 입니다.
map-service가 느려지면 place-service의 요청 스레드가 최대 3초씩 붙잡혀 **연쇄로 무너질 수 있습니다.**

→ 스파이크 테스트로만 재현되는 패턴입니다.

---

## 4. 테스트 5종 — 각각이 답하는 질문

**중요: "병목 확인"은 별도 테스트가 아닙니다.**

```
스트레스 테스트  →  "언제, 얼마에서 무너지는가"   (증상)
서버 관측       →  "무엇 때문에 무너졌는가"       (원인)  ← 이게 병목 확인
```

k6는 클라이언트 입장의 증상만 봅니다. p95가 튀는 건 알려주지만 그게 커넥션 풀 고갈인지 N+1인지 GC인지는 모릅니다.
병목은 **부하가 걸린 순간 서버 안을 들여다보는 관측 체계**(§7)로 찾습니다.

```
① 탐색 램프    /‾‾‾‾‾‾        "우리 한계가 대충 어디쯤?"       5분
② 평상 부하    ▁▁▁▁▁▁▁▁       "정상 상태 기준 수치는?"        10분
③ 스트레스     ▁▂▃▄▅▆▇█       "어디서 어떻게 무너지나?"        15분
④ 스파이크     ▁▁█▁▁█▁▁       "급증을 견디고 회복하나?"         8분
⑤ 지속(Soak)   ▔▔▔▔▔▔▔▔▔▔     "오래 돌리면 나빠지나?"          30분
```

| | 목적 | 판정 기준 | 특히 볼 것 |
|---|---|---|---|
| **① 탐색** | 한계치(knee) 확정 | — (수치 정하는 용도) | 이후 모든 RPS의 기준값 |
| **② 평상** | **비교용 공식 수치** | p50/p95/p99 기록 | 가설 ①②의 비용 |
| **③ 스트레스** | 병목 위치 | 부하 제거 후 **회복하는가** | 가설 ③ |
| **④ 스파이크** | 탄력성 | **복구 시간** (버텼나 ✕ → 돌아왔나 ○) | 가설 ④ |
| **⑤ 지속** | 시간에 따른 열화 | 절대값 ✕ → **기울기** ○ | outbox 적체, 힙 추세 |

### 부하량은 찍어서 정하지 않습니다

"50 RPS로 합시다"를 정할 근거가 지금 없습니다. 너무 낮으면 개선이 안 보이고, 너무 높으면 이미 무너진 구간이라 노이즈만 큽니다.

**① 탐색 램프로 knee(p95가 급격히 꺾이는 지점)를 먼저 구하고, 나머지를 상대값으로 잡습니다.**

```
평상 부하  = knee × 0.5      스파이크  = knee × 2.0
스트레스   = knee × 1.5      지속      = knee × 0.5 (세기 아닌 시간이 목적)
```

이렇게 하면 리팩터링 후 knee가 올라가도 같은 논리로 재측정할 수 있어 비교가 계속 유효합니다.

### 부하 모델은 `constant-arrival-rate` (open model)

전후 비교가 목적이면 이게 정답입니다.

```
VU 100명 고정 (closed)              초당 N건 고정 (open)
서버 느려짐 → 요청도 느려짐          서버 느려짐 → 부하는 그대로
= 부하가 서버 상태에 끌려다님         = 부하가 독립 변수로 고정
→ 개선해도 "처리량 그대로" 착시      → 개선분이 응답시간에 그대로 드러남
```

### 측정 경로는 두 개 — 회차마다 둘 다 잽니다 (`TARGET`)

```
TARGET=gateway   k6 ──Bearer JWT──→ gateway:8080 ──X-User-Id──→ map / place / user
TARGET=direct    k6 ──X-User-Id───────────────────→ map:8083 / place:8082
```

| | `gateway` (기본) | `direct` |
|---|---|---|
| 인증 | JWT를 k6가 직접 HS256 서명 | `X-User-Id` 헤더 |
| 재는 것 | 사용자가 겪는 **end-to-end** | **서비스 자체** (프록시 노이즈 제외) |
| 쓰임 | "우리 서비스 p95"라고 말할 수 있는 공식 수치 | 병목 사냥 |

하나를 고르는 게 아닙니다. **두 수치의 차이가 곧 게이트웨이 비용**이라 둘 다 재면 정보가 늘어납니다.

> 게이트웨이는 클라이언트가 보낸 `X-User-Id`를 무조건 버립니다. 그래서 `direct`는 게이트웨이를
> 안 거칠 때만 성립하고, 헤더를 위조해 게이트웨이를 우회할 수는 없습니다.

### 회차 규칙

- **회차 사이 서비스 재기동** — 앞 테스트의 힙·캐시 상태가 다음 회차를 오염시키면 비교가 깨집니다
- **워밍업 2분 제외** — JIT 컴파일 전 구간은 느립니다. 리팩터링 후에도 똑같이 버려야 공정합니다
- **`dropped_iterations > 0`이면 결과 폐기** — k6가 목표 부하를 못 낸 것. 노트북에서 k6와 서버가 CPU를 나눠 쓰므로 실제로 발생할 수 있습니다

---

## 5. 시나리오

엔드포인트를 하나씩 때리는 건 마이크로벤치입니다. **실제 화면 흐름대로** 묶어야 의미가 있습니다.

| # | 시나리오 | 호출 흐름 | 측정 의도 | 상태 |
|---|---|---|---|---|
| **S1** | 홈 진입 | `GET /maps?sort=POPULAR` + `/maps/official` + `/maps/recommendations` | tags N+1 · **페이지 깊이** · CPU 스코어링 | 작성됨 |
| **S2** | 지도 상세 진입 | `GET /maps/{id}` → `GET /places?mapId=` + `GET /maps/{id}/members` | **서비스 간 홉 3종 전부 통과** · 페이지 깊이 | 작성됨 |
| **S5** | 혼합 | 읽기 7 : 쓰기 3 | 읽기·쓰기 경합 | 작성됨 |
| S3 | 멤버 목록 단독 | `GET /maps/{id}/members` | user-service fan-out 격리 | 예정 |
| S4 | 장소 등록 단독 | `POST /places` | 쓰기 + 홉 비용 | 예정 |
| — | 인스타 추출 / 중복 등록 경합 | `manual/instagram-extract.js` | **수동 전용** (과금·정합성) | 작성됨 |

**S2가 대표 시나리오인 이유**: 서비스 간 홉 3종(place→map, map→user)을 전부 지나는 유일한 경로라 병목이 가장 잘 드러납니다.
**S5를 같이 두는 이유**: 읽기 전용으로만 재면 락·커넥션 경쟁이 안 보입니다.

> S3/S4를 아직 안 만든 이유: 첫 실행에서는 시드·토큰·포트 문제가 **반드시** 터집니다.
> 스크립트가 적을 때 잡는 게 빠릅니다. 돌아가는 걸 확인하고 붙입니다.

### 얕은 페이지 / 깊은 페이지를 나눠 잽니다

offset 페이지네이션은 뒤로 갈수록 DB가 앞의 행을 전부 읽고 버립니다. 섞어서 재면 그 비용이 평균에 묻힙니다.
그래서 S1(지도 목록)과 S2(장소 목록)는 **얕은 페이지(0~4)와 깊은 페이지를 50:50**으로 보고, 태그를 나눠 붙입니다.

```
GET /maps?sort=POPULAR (shallow)     ← 사용자가 실제로 많이 보는 구간
GET /maps?sort=POPULAR (deep)        ← offset 비용이 드러나는 구간
```

깊은 페이지가 의미를 가지려면 데이터가 몰려 있어야 합니다. 그래서 시드가 **장소를 몰아넣은 지도**를 따로 심습니다(§6).

### 멤버십을 맞춰서 호출합니다 ⚠️

조회·등록 모두 "그 지도의 멤버냐"를 검사합니다(place-service → map-service `getMemberInfo`).
`userId`와 `mapId`를 각각 독립적으로 랜덤 추출하면 **거의 전부 403**이 나서 인가 실패 경로만 재게 됩니다.

`seed.sql`의 멤버십 산식이 결정적이라 `lib/config.js`의 `memberOf(mapId)`가 그대로 재현합니다.
**mapId를 먼저 뽑고, 그 지도의 실제 멤버를 뽑습니다.** 시드 산식을 바꾸면 `memberOf()`도 같이 바꿔야 합니다.

### 시나리오 × 테스트 조합

5×5 = 25회는 과합니다. 필요한 조합만 돌립니다 (총 13회, 약 1시간 20분).

| | 탐색 | 평상 | 스트레스 | 스파이크 | 지속 |
|---|:---:|:---:|:---:|:---:|:---:|
| S1 홈 진입 | | ✅ | | | |
| **S2 지도 상세** | ✅ | ✅ | ✅ | ✅ | ✅ |
| S3 멤버 목록 | | ✅ | | | |
| S4 장소 등록 | | ✅ | | | |
| **S5 혼합** | | ✅ | ✅ | ✅ | ✅ |

여기에 `TARGET=gateway|direct` 두 경로가 곱해집니다. 평상(baseline)만 양쪽 다 재고,
나머지는 `direct` 하나로 충분합니다 — 무너지는 지점을 찾는 게 목적이라 프록시 구간은 노이즈입니다.

---

## 6. 시드 데이터 — 이게 없으면 측정 자체가 무의미

**빈 DB에 부하를 걸면 모든 쿼리가 10행짜리 테이블을 읽어서 전부 1ms로 나옵니다.**
그 상태로 인덱스를 추가하면 개선폭이 0으로 보입니다.

**`load-test/seed/seed.sql`** — 재실행 안전(대상 테이블 `TRUNCATE` 후 재적재)하고, 끝에 `ANALYZE`까지 돕니다.

```bash
docker compose exec -T postgres psql -U moamap -d moamap -f - < load-test/seed/seed.sql
```

| 테이블 | 건수 | 이유 |
|---|---|---|
| `users` | 5,000 | 토큰 풀 + 프로필 벌크 조회 대상 |
| `map_entity` | 7,020 (community ~5,000 / private ~2,000 / **official 20**) | 목록 페이징이 의미를 가지려면 |
| `map_tag` | ~21,000 | **가설 ②(tags N+1)를 드러내는 핵심 데이터** |
| `map_member` | ~49,000 | 지도당 OWNER 1 + 멤버 6 |
| `place` | 60,000 + **몰린 지도 12,000** | 장소 목록 페이징 |
| `place_review` | 300,000 | 평점 집계 경로 |

### "장소가 몰린 지도"를 따로 심습니다 ⭐

장소 6만 개를 지도 7천 개에 흩뿌리면 **지도당 8.5개**입니다. 페이지 1장으로 끝나서
"장소가 많을 때 느려진다"를 잴 수 없습니다. 그래서 표본 두 개를 따로 만듭니다.

| id | 유형 | 장소 | 멤버 | 쓰임 |
|---|---|---|---|---|
| `900001` | COMMUNITY | 6,000 | 60 | **S2 대상.** 깊은 페이지 + 멤버 목록 fan-out |
| `900002` | PRIVATE(personal) | 6,000 | 1 (소유자) | 개인 지도 경로 비교 |

id를 고정값으로 박는 이유: **k6가 이 지도를 지목해서 때려야 하기 때문**입니다.
`lib/config.js`의 `SEED.hotMapId` / `SEED.personalMapId`와 짝입니다.

### 시드에 심어둔 함정들

의도적으로 섞은 값들입니다. 없으면 놓치는 비용이 생깁니다.

- **소프트 삭제된 장소 50건에 1건** — 삭제 필터 누락이 드러나게
- **PENDING 장소 20건에 1건** — 상태 필터 경로
- **비정규화 카운트(`member_count`, `place_count`, `avg_rating`) 사후 정합** — 실제 API가 이 값으로 정렬·표시하므로 현실과 어긋나면 측정이 왜곡됩니다
- **OFFICIAL 지도 20개** — 없으면 `GET /maps/official`이 빈 페이지를 주는데, 빈 응답은 항상 빠르므로 "공식 지도 목록이 빠르다"는 잘못된 수치가 나옵니다

### 그 밖의 주의점

- **적재 후 반드시 `ANALYZE`** — 대량 INSERT 직후엔 통계가 없어 옵티마이저가 엉뚱한 실행 계획을 고릅니다. 그 상태로 측정하면 병목 분석이 전부 틀립니다 (스크립트 끝에 포함)
- **스키마 분리 구조라 FK가 없습니다** — 서비스 간 참조 정합성은 시드가 직접 맞춰야 합니다
- **아직 `setseed()`를 안 씁니다** — 행 수와 id는 결정적이지만 좌표·시각·평점은 매 적재마다 달라집니다.
  before/after 사이에 시드를 다시 적재한다면 `setseed()`를 넣어야 완전히 같은 데이터가 됩니다 (미구현)
- **개인정보 금지** — 실제 사용자 데이터를 절대 넣지 않습니다. 전부 합성 값(`perf1`, `테스트 지도 1`)
- **S5는 쓰기를 섞으므로 행이 늘어납니다** — 회차 사이에 정리하거나 시드를 재적재하세요
  ```sql
  DELETE FROM place_service.places WHERE kakao_place_id LIKE 'loadtest-%';
  ```

### 순서 의존성 ⚠️

`ddl-auto: update`라 **테이블이 앱 기동으로 생성**됩니다.

```
서비스 기동 (스키마 생성)  →  시드 적재 (+ANALYZE)  →  부하 테스트
```

시드를 먼저 넣으려 하면 테이블이 없어서 실패합니다.

---

## 7. 관측 — 병목을 실제로 찾는 방법

부하 테스트의 값어치는 여기서 갈립니다. 이게 없으면 "느립니다"로 끝납니다.

```
[1층] k6                    →  증상: p95, 에러율, 처리량            ✅ 구성됨
[2층] Prometheus + Grafana  →  앱 내부: 커넥션 풀, 스레드, 힙, GC    ✅ 구성됨
[3층] pg_stat_statements    →  DB: 어떤 쿼리가 몇 번, 총 몇 ms      ⬜ 미구성
```

### 2층 — Prometheus + Grafana

```bash
docker compose -f docker-compose.yml -f load-test/compose.loadtest.yml up -d
# Prometheus  http://localhost:9090
# Grafana     http://localhost:3001   (로컬 전용이라 로그인 없음)
```

- 서비스는 `bootRun`으로 호스트에서 돌기 때문에 컨테이너에서 `host.docker.internal:8081~8084`로 스크랩합니다
- 스크랩 간격 **5초** — 기본 15초는 몇 분짜리 테스트엔 그래프가 너무 성깁니다
- **게이트웨이(8080)는 의도적으로 제외**합니다. LoadBalancer로 공인 IP에 물려 있어 지표가 공개됩니다.
  → 게이트웨이 구간 수치는 k6(`TARGET=gateway`) 쪽으로만 봅니다
- `percentiles-histogram: http.server.requests: true` — 이게 없으면 Micrometer가 count/sum만 내보내
  **서버 쪽 p95를 못 냅니다**. 4개 서비스에 켜뒀습니다
- Grafana에 데이터소스는 자동 등록되지만 **대시보드 패널은 아직 없습니다.** 최소 4개는 만들어야 합니다:
  `hikaricp_connections_pending` / `http_server_requests` p95 by uri / `jvm_gc_pause_seconds` / `jvm_memory_used_bytes`

### 증상 → 원인 매핑표

미리 만들어두면 결과 해석이 기계적으로 됩니다.

| k6에서 보이는 증상 | 의심 지점 | 확인 방법 |
|---|---|---|
| p50 정상, **p99만 폭발** | 커넥션 풀 대기 | `hikaricp.connections.pending` |
| **전 구간 균등 상승** | DB 포화 / CPU 포화 | `pg_stat_statements` 총 시간, 컨테이너 CPU |
| place만 느리고 map은 정상 | place 자체 로직 | place-service 힙·GC |
| **place가 map보다 더 느림** | 서비스 간 홉 대기 | 두 서비스 p95 차이 = 홉 비용 |
| 요청수 대비 **쿼리수가 20배** | **N+1 (가설 ②)** | `pg_stat_statements`의 `calls` |
| 시간이 갈수록 느려짐 | 누수 / 테이블 증가 | 힙 추세, `outbox_event` 행수 |

### 3층 — pg_stat_statements (아직 미구성 ⬜)

**가설 ②(N+1)는 쿼리 수를 봐야 확정됩니다.** k6도 Actuator도 그걸 못 봅니다.
"요청 1건당 쿼리 몇 개"가 나와야 `21 → 2` 같은 숫자로 개선을 증명할 수 있습니다.

```yaml
# docker-compose.yml 의 postgres 에 추가 필요
command: >
  postgres -c shared_preload_libraries=pg_stat_statements
           -c pg_stat_statements.track=all
```

수집 규칙:
- 각 테스트 **시작 직전 `SELECT pg_stat_statements_reset();`**, 종료 직후 TOP 20 덤프
- 그래야 그 회차만의 쿼리 프로파일이 나옵니다

### N+1 실험 스위치

`map-service/application.yml`에 **주석 처리된 채로** 들어 있습니다.

```yaml
# default_batch_fetch_size: 100
```

베이스라인을 뽑은 뒤 주석을 풀고 재기동 → 같은 조건으로 재측정하면
`MapSummaryResponse.tags` 지연 로딩이 IN 절로 묶이면서 쿼리 수가 얼마나 줄어드는지 나옵니다.
**이게 이번 측정의 첫 번째 검증 대상입니다.**

### 지속 테스트에서 특별히 볼 것 — `outbox_event`

`OutboxPublisher`는 2초마다 폴링하는데 **정리(cleanup)는 매일 04시 크론 1회**입니다.
30분 부하 동안 발행된 이벤트는 전혀 정리되지 않고 계속 쌓입니다.

→ **테이블이 커질수록 폴링 쿼리가 느려지는지** 확인합니다. 30분 이상 돌려야만 보이고, 운영에서 하루치가 쌓이면 문제가 될 수 있는 지점입니다.

---

## 8. 실행 방법

### 사전 준비

```bash
# k6 설치 (단일 바이너리, 제거는 brew uninstall k6)
brew install k6
```

> 도커로 실행(`grafana/k6`)도 가능하지만, 도커 네트워크 계층이 지연 측정에 노이즈를 더합니다.
> 밀리초 단위 측정이 결과의 전부라 네이티브를 권합니다.

### 환경 변수

`load-test/.env.loadtest` 파일을 만들어 사용합니다. **이 파일은 gitignore 대상입니다.**
`.env.loadtest.example`를 복사해서 값을 채우세요.

| 변수 | 설명 |
|---|---|
| `TARGET` | `gateway`(기본) / `direct` — 측정 경로 |
| `BASE_URL` | 게이트웨이 주소 (기본 `http://localhost:8080`) |
| `MAP_URL` `PLACE_URL` `USER_URL` | `TARGET=direct` 일 때 서비스 주소 |
| `JWT_SECRET` | **테스트 전용 시크릿.** 운영 값 절대 사용 금지. 32자 이상. `direct` 면 불필요 |
| `SEED_*` | 시드 규모. `seed/seed.sql` 의 `\set` 과 일치시킵니다 |
| `TARGET_RPS` | 목표 부하. **탐색 램프로 knee를 구한 뒤 채웁니다** |

### 실행

```bash
# 0) 환경변수
cp load-test/.env.loadtest.example load-test/.env.loadtest   # 값 채우기
set -a && . load-test/.env.loadtest && set +a                 # k6는 .env를 안 읽습니다

# 1) 인프라 + 관측 스택 (레포 루트에서)
docker compose -f docker-compose.yml -f load-test/compose.loadtest.yml up -d

# 2) 서비스 기동 — ddl-auto: update 라 여기서 테이블이 생깁니다
./gradlew :user-service:bootRun :map-service:bootRun :place-service:bootRun :gateway-service:bootRun

# 3) 시드 적재 (+ANALYZE 포함)
docker compose exec -T postgres psql -U moamap -d moamap -f - < load-test/seed/seed.sql

# 4) Smoke — 부하 측정이 아니라 스크립트 검증
k6 run -e MODE=smoke load-test/scripts/scenarios/s2-map-detail.js

# 5) 탐색 램프 → knee 확정
k6 run -e MODE=probe load-test/scripts/scenarios/s2-map-detail.js

# 6) 평상 부하 (baseline) — 두 경로 다
k6 run -e MODE=load -e TARGET_RPS=<knee×0.5> load-test/scripts/scenarios/s2-map-detail.js
k6 run -e MODE=load -e TARGET_RPS=<knee×0.5> -e TARGET=direct load-test/scripts/scenarios/s2-map-detail.js
```

`MODE`는 `smoke | probe | load | stress | spike | soak`, 시나리오는 `s1-home` / `s2-map-detail` / `s5-mixed` 입니다.

### Smoke에서 반드시 확인할 것

| 확인 | 실패 시 의미 |
|---|---|
| `check()` 통과율 100% | 스크립트 버그 |
| **응답 body에 실제 데이터가 있는가** | 빈 배열이면 시드/파라미터 오류 → 이걸 놓치면 **"빈 응답을 빠르게 반환하는 것"을 측정하게 됩니다** |
| 에러율 0% | 인증·라우팅 문제 |

---

## 9. 결과 기록 규칙

```
load-test/results/
└── 2026-08-16-baseline/          # 날짜 + 라벨
    ├── s2-load.json              # k6 summary
    ├── s2-stress.json
    ├── pg_stat_statements.txt    # 쿼리 TOP 20
    └── SUMMARY.md                # 사람이 읽는 표
```

- **커밋할 것**: 스크립트, 시드, 요약 결과(`SUMMARY.md`, summary JSON)
- **커밋하지 않을 것**: k6 raw 출력(용량 큼), `.env.loadtest`
- 요약은 **반드시 커밋**해야 나중에 diff로 before/after 비교가 됩니다

### 최종 비교표 목표 형태

| 시나리오 | p95 (전) | p95 (후) | 쿼리수/req |
|---|---|---|---|
| S2 지도 상세 | ? | ? | ? → ? |

**"N+1을 고쳤습니다"가 아니라 "쿼리 21→2, p95 320→95ms"** 로 남기는 게 목적입니다.

---

## 10. 협업 포인트 — 팀원 도움이 필요한 부분

리뷰·수정 환영합니다. 특히 아래 항목은 의견이 갈릴 수 있어 같이 정하면 좋겠습니다.

| # | 항목 | 현재 초안 | 논의가 필요한 이유 |
|---|---|---|---|
| 1 | **시드 규모** | users 5,000 / place 50,000 | 너무 작으면 병목이 안 보이고, 너무 크면 적재·재현이 번거롭습니다 |
| 2 | **시나리오 가중치** | S5 읽기 7 : 쓰기 3 | 실제 사용 패턴에 대한 감이 있으시면 조정하고 싶습니다 |
| 3 | **리소스 제한값** | 미정 | 현재 k8s에 CPU limit이 없습니다. 부하 테스트는 제한을 고정해야 재현되는데, 어떤 값이 우리 목표에 맞는지 |
| 4 | **추가 시나리오** | S1~S5 | 빠뜨린 중요 화면 흐름이 있는지 |
| 5 | **판정 임계값** | 1차는 느슨하게 | 베이스라인 확정 후 회귀 게이트로 조입니다. 목표 SLO가 있으면 반영하겠습니다 |

### 진행 상황

- [x] 테스트 설계 (이 문서)
- [x] k6 스크립트 (S1, S2, S5 + manual)
- [x] 관측 스택 — Prometheus + Grafana + Micrometer 히스토그램
- [x] 시드 SQL (몰린 지도·공식 지도·함정 데이터 포함)
- [x] 두 브랜치 통합 (`chore/성능수치화` + `chore/#85`)
- [ ] **Smoke 검증** ← 다음 할 일. 여기서 시드·토큰·403 문제가 터집니다
- [ ] Grafana 대시보드 패널
- [ ] `pg_stat_statements` 활성화 (쿼리 수 없이는 N+1 개선을 증명 못 합니다)
- [ ] 탐색 램프 → knee 확정
- [ ] 베이스라인 측정 (gateway / direct)
- [ ] `default_batch_fetch_size` 켜고 재측정 → 첫 before/after
- [ ] 결과 문서화
- [ ] k6 `handleSummary`로 결과 JSON 자동 저장 (회차 13번을 손으로 옮길 수 없습니다)
- [ ] S3, S4 시나리오

---

## 11. 보안 주의사항

이 레포는 **퍼블릭**입니다. 아래를 지켜주세요.

| 항목 | 규칙 |
|---|---|
| **JWT 시크릿** | 코드에 하드코딩 금지. `__ENV.JWT_SECRET`로만 주입. **운영 시크릿 절대 사용 금지** |
| **`.env.loadtest`** | gitignore 대상. 커밋하지 마세요 |
| **결과 파일** | `Authorization` 헤더나 토큰이 포함되지 않았는지 확인 후 커밋 |
| **시드 데이터** | 실제 사용자 정보 금지. 전부 합성 값 |
| **Actuator** | `prometheus` 엔드포인트는 8081~8084에만 엽니다. **게이트웨이(8080)는 공인 IP에 물려 있어 제외** |
| **과금** | §2의 제외 엔드포인트를 자동 스위트(S1/S2/S5)에 넣지 마세요. `manual/`은 수동 실행 전용입니다 |

---

## 참고

- 이슈: #85
- k6 부하 모델: [Arrival-rate executors](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/)
