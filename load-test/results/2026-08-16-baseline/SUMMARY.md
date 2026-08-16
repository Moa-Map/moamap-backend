# 성능 베이스라인 1차 측정 — 2026-08-16

> 첫 실행 검증 회차. 스크립트·시드·관측 스택이 실제로 도는지 확인하고, 가설 ②(tags N+1)를 실측했습니다.
> **정식 베이스라인(MODE=load 10분)은 아직입니다.** 아래는 smoke와 탐색 램프 결과입니다.

## 측정 환경

| 항목 | 값 |
|---|---|
| 실행 | 로컬 (macOS, k6와 서버가 같은 머신) |
| 서비스 | user/place/map/gateway 4개 `bootRun` (notification 미기동) |
| DB | PostgreSQL 16 (도커), 단일 DB 스키마 분리 |
| 시드 | `load-test/seed/seed.sql` — users 5,000 / maps 7,022 / places 72,000 / reviews 300,000 |
| 경로 | `TARGET=gateway` (게이트웨이 경유, JWT 서명) |
| 관측 | Prometheus 5초 스크랩 + Micrometer 히스토그램 |

⚠️ k6와 서버가 CPU를 공유하는 노트북 측정입니다. **절대값이 아니라 before/after 차이만 유효합니다.**

---

## 1. Smoke (MODE=smoke, 1 VU × 1분)

| 시나리오 | checks | 실패 | dropped | p95 |
|---|---|---|---|---|
| S1 홈 진입 | **100%** (342/342) | 0.00% | 0 | 63.47ms |
| S2 지도 상세 | **100%** (336/336) | 0.00% | 0 | 54.74ms |
| S5 혼합 | **100%** (97/97) | 0.00% | 0 | 76.45ms |

무부하 상태 요청별 p95:

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

**얕은/깊은 페이지 차이가 거의 없습니다** (57.97 → 60.40ms). offset 페이지네이션 비용은
현재 데이터 규모(6,000행)에서는 병목이 아닙니다. 가설 목록에서 우선순위를 내려도 됩니다.

---

## 2. 가설 ② tags N+1 — 확정 ✅

`pg_stat_user_tables`의 스캔 수 차분으로 요청당 쿼리 수를 셌습니다 (200요청 기준).

### BEFORE — `default_batch_fetch_size` 꺼짐 (현재 develop 상태)

| 엔드포인트 | `map_tag` 스캔/요청 | `map_entity` | `map_member` |
|---|---:|---:|---:|
| `GET /maps?sort=POPULAR&size=20` | **18.3** | 1.8 | 0.9 |
| `GET /maps/official?size=20` | **20.3** | 2.0 | 1.0 |

페이지 20건 → 태그 쿼리 20개. **교과서적인 N+1입니다.**

### AFTER — `default_batch_fetch_size: 100`

| 엔드포인트 | `map_tag` 스캔/요청 | `map_entity` | `map_member` |
|---|---:|---:|---:|
| `GET /maps?sort=POPULAR&size=20` | **1.0** | 1.9 | 1.0 |
| `GET /maps/official?size=20` | **1.1** | 2.2 | 1.1 |

### 결론

```
map_tag 쿼리   20.3 → 1.1  (요청당, -95%)
요청당 총 쿼리  약 23 → 약 4
```

설정 **한 줄**로 해결됩니다. `map-service/src/main/resources/application.yml:24`에 주석으로 대기 중입니다.

---

## 3. 탐색 램프 (MODE=probe, 1→200 iter/s, 5분) — S1 홈 진입

S1은 이터레이션당 3요청이라 아래 "서버 처리량"은 k6 이터레이션 rate의 약 3배입니다.

### BEFORE

| 서버 처리량 (req/s) | 서버 p95 |
|---:|---:|
| ~110 | 22ms |
| 227 | 22ms |
| 317 | 39ms |
| 345 | **79ms** ← 꺾이기 시작 |
| 362 | **469ms** ← 무너짐 |
| 379~391 | 624~700ms (천장) |

**knee ≈ 320 req/s** (k6 기준 약 107 iter/s)

### AFTER

| 서버 처리량 (req/s) | 서버 p95 |
|---:|---:|
| ~116 | 22ms |
| 293 | 15ms |
| 413 | 17ms |
| 502 | **20ms** ← 아직 평평 |
| 521 | **274ms** ← 여기서 꺾임 |
| 551 | 402ms |

**knee ≈ 500 req/s** (k6 기준 약 167 iter/s)

### 요약

| | BEFORE | AFTER | 변화 |
|---|---:|---:|---|
| knee (서버 req/s) | ~320 | ~500 | **+56%** |
| 전체 p95 (k6, 램프 전체) | 3.43s | **37.38ms** | **-99%** |
| 같은 부하(~320 req/s)에서 p95 | 39ms | 15ms | **-62%** |
| `dropped_iterations` | 4,720 | 256 | -95% |
| 총 처리 요청 | 76,287 | 89,679 | +18% |
| checks 성공률 | 100% | 100% | — |

에러율은 양쪽 모두 **0.00%** 였습니다. 무너지는 방식이 실패가 아니라 **지연 증가**입니다.

---

## 4. 다음 회차에 할 것

- [ ] 정식 베이스라인: `MODE=load -e TARGET_RPS=53` (knee×0.5) 10분, S1/S2/S5
- [ ] `TARGET=direct`로 같은 측정 → 차이 = 게이트웨이 비용
- [ ] S2·S5도 probe 돌려 시나리오별 knee 확보
- [ ] `pg_stat_statements` 활성화 — 지금은 테이블 스캔 수로 근사 중
- [ ] 가설 ①(서비스 간 홉), ③(커넥션 풀), ④(장애 전파)는 미검증

## 측정하지 않은 것

- 스트레스·스파이크·지속(soak) 테스트
- `TARGET=direct` 경로
- notification-service (미기동)
- Gemini·카카오 연동 엔드포인트 (과금 — 의도적 제외)
