# 성능 베이스라인 1차 측정 — 2026-08-16

> **성격: 첫 실행 검증 회차.** 스크립트·시드·관측 스택이 실제로 도는지 확인하고,
> 가설 ②(tags N+1)를 실측했습니다.
> **정식 베이스라인(`MODE=load` 10분)은 아직입니다.** 아래는 smoke와 탐색 램프(probe) 결과입니다.

| | |
|---|---|
| 브랜치 | `chore/#85/k6-load-test-baseline` |
| 이슈 | #85 |
| 원시 결과 | 같은 디렉터리의 `smoke-*.txt`, `probe-*.txt` |

---

## 0. 측정 환경

| 항목 | 값 |
|---|---|
| 실행 위치 | 로컬 (macOS) — **k6와 서버가 같은 머신** |
| 서비스 | user(8081) / place(8082) / map(8083) / gateway(8080) `bootRun` |
| 미기동 | notification(8084) — 껍데기 모듈이라 제외 |
| DB | PostgreSQL 16 (도커), 단일 DB에 서비스별 스키마 분리 |
| 시드 | `load-test/seed/seed.sql` |
| 측정 경로 | `TARGET=gateway` (게이트웨이 경유, JWT 직접 서명) |
| 관측 | Prometheus 5초 스크랩 + Micrometer 히스토그램 |
| k6 | 네이티브 설치 (`brew`) |

### 시드 규모 (적재 3.5초)

| 테이블 | 건수 |
|---|---:|
| `users` | 5,000 |
| `map_entity` | 7,022 (COMMUNITY 5,001 / PRIVATE 2,001 / OFFICIAL 20) |
| `map_tag` | 21,063 |
| `map_member` | 49,054 |
| `places` | 72,000 (일반 60,000 + 몰린 지도 12,000) |
| `place_reviews` | 300,000 |

고정 id: `900001` 커뮤니티(장소 6,000·멤버 61), `900002` 개인(장소 6,000·멤버 1)

### ⚠️ 해석 주의

k6와 서버가 CPU를 공유하는 노트북 측정입니다.
**절대값은 의미가 없고, 같은 환경에서 잰 before/after 차이만 유효합니다.**

---

## 1. 셋업 검증

| 항목 | 결과 |
|---|---|
| JWT 시크릿 | `.env` ↔ `.env.loadtest` 일치, 둘 다 gitignore 확인 |
| Prometheus 타깃 | 8081·8082·8083 `up` / 8084 `down`(미기동, 정상) |
| 히스토그램 버킷 | 서비스당 138개 노출 → **서버 p95 계산 가능** |
| 서비스 헬스 | 8080·8081·8082·8083 전부 `UP` |
| 게이트웨이 인증 | 아래 6개 전부 **200** |

```
GET /api/v1/maps/900001                    200
GET /api/v1/places?mapId=900001&size=5     200
GET /api/v1/maps/900001/members            200
GET /api/v1/maps/official?size=5           200
GET /api/v1/maps?sort=POPULAR&size=5       200
GET /api/v1/maps/recommendations?size=5    200
```

---

## 2. Smoke (MODE=smoke, 1 VU × 1분) — 3/3 통과

| 시나리오 | checks | 실패율 | dropped | p95 |
|---|---|---|---|---|
| S1 홈 진입 | **100%** (342/342) | 0.00% | 0 | 63.47ms |
| S2 지도 상세 | **100%** (336/336) | 0.00% | 0 | 54.74ms |
| S5 혼합 | **100%** (97/97) | 0.00% | 0 | 76.45ms |

`POST /places`가 2xx 통과 — **통합 때 고친 403 버그(멤버십 쌍 불일치)가 실환경에서 해소됨을 확인.**
응답 body 데이터 검증도 전부 통과 → 시드가 제대로 물렸습니다.

### 무부하 기준 요청별 p95

```
S2  GET /maps/:id ................ 37.17ms
    GET /maps/:id/members ........ 47.92ms
    GET /places?mapId (shallow) .. 57.97ms
    GET /places?mapId (deep) ..... 60.40ms

S1  GET /maps/official ........... 56.66ms
    GET /maps?sort=POPULAR (sh) .. 57.03ms
    GET /maps?sort=POPULAR (deep)  60.74ms
    GET /maps/recommendations .... 68.23ms

S5  GET /maps/:id ................ 26.24ms
    GET /places?mapId ............ 42.66ms
    GET /maps/official ........... 66.25ms
    GET /maps/recommendations .... 79.82ms
    POST /places ................. 146.51ms
```

### 발견 — 페이지 깊이는 병목이 아닙니다

얕은 페이지 57.97ms vs 깊은 페이지 60.40ms. **차이 4%.**
offset 페이지네이션 비용은 현재 규모(단일 지도 6,000행)에서 문제가 아닙니다.
가설 우선순위에서 내려도 됩니다. 데이터가 10배가 되면 다시 봅니다.

---

## 3. 가설 ② tags N+1 — 확정 ✅

`MapSummaryResponse.tags`가 `@ElementCollection`(지연 로딩)이라 페이지 건수만큼
태그 쿼리가 붙는다는 가설. **실측으로 확정됐습니다.**

### 측정 방법

`pg_stat_statements`가 아직 없어서 `pg_stat_user_tables`의 스캔 수
(`seq_scan + idx_scan`) 전후 스냅샷 차분으로 "요청당 쿼리 수"를 셌습니다.

> 표본 200요청. 30요청으로는 HikariCP 유휴 커넥션이 통계를 붙들고 있어
> 다음 회차로 새는 오차가 컸습니다(pg는 트랜잭션 종료 시 백엔드 단위로 통계를 flush).

### BEFORE — `default_batch_fetch_size` 꺼짐 (현재 develop 상태)

| 엔드포인트 | `map_tag` | `map_entity` | `map_member` |
|---|---:|---:|---:|
| `GET /maps?sort=POPULAR&size=20` | **18.3** | 1.8 | 0.9 |
| `GET /maps/official?size=20` | **20.3** | 2.0 | 1.0 |

페이지 20건 → 태그 쿼리 20개. 교과서적 N+1입니다.

### AFTER — `default_batch_fetch_size: 100`

| 엔드포인트 | `map_tag` | `map_entity` | `map_member` |
|---|---:|---:|---:|
| `GET /maps?sort=POPULAR&size=20` | **1.0** | 1.9 | 1.0 |
| `GET /maps/official?size=20` | **1.1** | 2.2 | 1.1 |

### 결론

```
map_tag 쿼리    20.3 → 1.1   (요청당, -95%)
요청당 총 쿼리   약 23 → 약 4
```

**설정 한 줄**로 해결됩니다.
`map-service/src/main/resources/application.yml:24`에 주석 처리된 채 대기 중입니다.

---

## 4. 탐색 램프 (MODE=probe, 1→200 iter/s, 5분) — S1 홈 진입

Prometheus 시계열(`histogram_quantile(0.95, ...)`)에서 뽑았습니다.
S1은 이터레이션당 3요청이므로 아래 "서버 처리량"은 k6 이터레이션 rate의 약 3배입니다.

### BEFORE

| 서버 처리량 (req/s) | 서버 p95 | |
|---:|---:|---|
| 110 | 22ms | |
| 227 | 22ms | |
| 317 | 39ms | |
| 345 | **79ms** | ← 꺾이기 시작 |
| 362 | **469ms** | ← 무너짐 |
| 379~391 | 624~700ms | 천장 |

**knee ≈ 320 req/s** (k6 기준 약 107 iter/s)

### AFTER

| 서버 처리량 (req/s) | 서버 p95 | |
|---:|---:|---|
| 116 | 22ms | |
| 293 | 15ms | |
| 413 | 17ms | |
| 502 | **20ms** | ← 아직 평평 |
| 521 | **274ms** | ← 여기서 꺾임 |
| 551 | 402ms | |

**knee ≈ 500 req/s** (k6 기준 약 167 iter/s)

### 종합

| 지표 | BEFORE | AFTER | 변화 |
|---|---:|---:|---|
| **knee (서버 req/s)** | ~320 | ~500 | **+56%** |
| **전체 p95 (k6, 램프 전 구간)** | 3.43s | **37.38ms** | **-99%** |
| 같은 부하(~320 req/s)에서 p95 | 39ms | 15ms | **-62%** |
| `dropped_iterations` | 4,720 | 256 | -95% |
| 총 처리 요청 | 76,287 | 89,679 | +18% |
| checks 성공률 | 100% | 100% | — |
| `http_req_failed` | 0.00% | 0.00% | — |

**에러율은 양쪽 모두 0.00%.**
무너지는 방식이 실패가 아니라 **지연 증가**입니다. 부하 테스트 없이는 보이지 않는 종류의 정보입니다.

---

## 5. 마무리 상태

- `application.yml` 은 **커밋 상태(주석)로 복구** — 스위치는 그대로 대기
- S5가 만든 장소 19건 삭제, `places` 72,000건 원상복구
- 결과 파일에 토큰·시크릿 미포함 확인

---

## 6. 다음 회차 할 일

- [ ] **정식 베이스라인**: `MODE=load -e TARGET_RPS=53` (knee×0.5) 10분 × S1/S2/S5
- [ ] `TARGET=direct` 로 같은 측정 → **차이 = 게이트웨이 비용**
- [ ] S2·S5도 probe 돌려 시나리오별 knee 확보
- [ ] `pg_stat_statements` 활성화 — 지금은 테이블 스캔 수로 근사 중
- [ ] Grafana 대시보드 패널 (`hikaricp_connections_pending`, `jvm_gc_pause_seconds`)

### 이번에 측정하지 않은 것

| 항목 | 사유 |
|---|---|
| 스트레스·스파이크·지속(soak) | 1차는 스크립트 검증이 목적 |
| `TARGET=direct` 경로 | 다음 회차 |
| 가설 ① 서비스 간 홉 | 미검증 |
| 가설 ③ 커넥션 풀 | 미검증 |
| 가설 ④ 장애 전파 | 스파이크 테스트 필요 |
| notification-service | 미기동 |
| Gemini·카카오 엔드포인트 | **과금 — 의도적 제외** |
