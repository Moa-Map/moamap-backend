// 인스타그램 릴스 등록 흐름 부하 테스트.
//
// ─── 실제 사용자 흐름 (이걸 알아야 아래 시나리오가 읽힌다) ────────────────────
//   1) 사용자가 릴스 URL/설명글을 붙여넣는다
//      → POST /api/v1/places/instagram-extractions
//      → 서버가 Gemini 1회 호출(설명글 → 장소 이름 추출) + 카카오 로컬 검색 N회(추출된 장소 수만큼, 직렬)
//      → 장소 "후보 목록"을 돌려준다. 이 단계는 DB를 건드리지 않는다.
//   2) 사용자가 후보 중 하나를 골라 지도에 담는다
//      → POST /api/v1/places (후보 값 + mapId + sourceType=INSTAGRAM)
//      → 서버가 map-service에 "이 유저가 이 지도 멤버냐" 물어보고(HTTP 1회), DB에 insert하고,
//        APPROVED면 장소 수 갱신 이벤트를 발행한다.
//   두 단계는 병목이 완전히 다르다 — 1)은 외부 API 쿼터, 2)는 서비스 간 호출 + DB 쓰기.
//   그래서 한 시나리오로 섞지 않고 아래처럼 나눠 잰다.
//
// ─── 시나리오 ────────────────────────────────────────────────────────────────
//   extract  : 1)번만. 외부 API(Gemini·카카오) 구간이 동시요청을 얼마나 받아내는지.
//              ⚠️ 실제 쿼터를 태운다. Gemini 무료 티어는 몇 VU만 돼도 429 → 503(PLACE_009)으로 떨어진다.
//   register : 2)번만. 매번 다른 kakaoPlaceId로 등록해서 정상 쓰기 경로의 처리량을 본다.
//              전제: 1번 유저가 900001 지도의 OWNER (scripts/perf-seed/seed.sql). 그래서 즉시 APPROVED로 저장된다.
//   race     : 2)번을 "여러 VU가 같은 장소를 동시에" 등록. 같은 지도+같은 kakaoPlaceId는 유니크 제약
//              (uk_places_map_kakao_place)으로 막혀야 한다. 기대 결과는 1건만 201, 나머지는 409.
//              500이 섞여 나오면 제약 위반이 그대로 새어나온 것이고, 201이 여러 건이면 중복이 뚫린 것이다.
//
// ─── 실행 ────────────────────────────────────────────────────────────────────
//   k6 run -e ONLY=register scripts/k6/instagram-extract-test.js
//   k6 run -e ONLY=race     scripts/k6/instagram-extract-test.js
//   k6 run -e ONLY=extract  scripts/k6/instagram-extract-test.js   # 외부 쿼터 소진 주의
//   k6 run scripts/k6/instagram-extract-test.js                    # 기본값 = register
//   VUS / DURATION 으로 동시성·시간 조절 (기본 10VU 30초)
//
//   register·race는 DB에 실제 행을 남긴다. 재실행 전에 정리:
//     DELETE FROM place_service.places WHERE kakao_place_id LIKE 'k6-%';
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const PLACE = __ENV.PLACE_URL || 'http://localhost:8082';
const VUS = Number(__ENV.VUS || 10);
const DURATION = __ENV.DURATION || '30s';

// seed.sql 과 맞춰야 하는 값. 어긋나면 전부 403(멤버 아님)이 나서 측정이 무의미해진다.
const MAP_ID = 900001; // COMMUNITY 지도
const OWNER_ID = 1; // 위 지도의 OWNER → 등록 시 즉시 APPROVED

const DESCRIPTIONS = [
  '성수동 카페 어니언 다녀왔어요 🥐 빵이 진짜 맛있음',
  '연남동 맛집 투어 - 연남서식당 고기 미쳤고, 카페 코야에서 마무리했어요',
  '망원동 데이트 코스: 소나기 브런치 → 망원한강공원 산책 → 카페 뎁스',
  '오늘 저녁은 을지로 을지면옥. 평양냉면 입문하기 좋음',
  '강남 성수 홍대 다 돌아본 결과 제일은 성수 대림창고였다',
];

// 상태코드별로 따로 센다. 실패 응답은 빠르게 떨어지기 때문에
// p95만 보면 "막혀서 빨리 죽는 중"을 "빠르다"로 착각하게 된다.
const byStatus = new Counter('resp_by_status');
const okDuration = new Trend('ok_duration', true);
const candidateCount = new Trend('extract_candidates');

const ONLY = __ENV.ONLY || 'register';
const ALL_SCENARIOS = {
  extract: { exec: 'extract' },
  register: { exec: 'register' },
  race: { exec: 'race' },
};
if (!ALL_SCENARIOS[ONLY]) {
  throw new Error(`ONLY는 ${Object.keys(ALL_SCENARIOS).join('|')} 중 하나여야 합니다. 받은 값: ${ONLY}`);
}

export const options = {
  scenarios: {
    [ONLY]: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      exec: ALL_SCENARIOS[ONLY].exec,
    },
  },
  // 합격 기준이 아니라 출력용. 절대 실패하지 않는 조건을 걸어 요약에 시나리오별 숫자가 찍히게 한다.
  thresholds: {
    'http_req_duration{status:200}': ['p(95)>=0'],
    'http_req_duration{status:201}': ['p(95)>=0'],
  },
};

function record(res, expectedStatuses) {
  byStatus.add(1, { status: String(res.status) });
  const ok = expectedStatuses.includes(res.status);
  if (ok) {
    okDuration.add(res.timings.duration);
  } else {
    // 실패는 원인이 전부다 — 상태코드와 본문 앞부분을 남긴다.
    console.warn(`unexpected status=${res.status} body=${String(res.body).slice(0, 200)}`);
  }
  check(res, { [`status in ${expectedStatuses}`]: () => ok });
  return ok;
}

// ── 시나리오 1: 추출만 ────────────────────────────────────────────────────────
export function extract() {
  const description = DESCRIPTIONS[Math.floor(Math.random() * DESCRIPTIONS.length)];
  const res = http.post(
    `${PLACE}/api/v1/places/instagram-extractions`,
    JSON.stringify({ url: 'https://www.instagram.com/reel/perf-test/', description }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'extract' },
      // 서버 read-timeout(Gemini 15s) + 카카오 N회보다 넉넉하게. 짧으면 k6가 먼저 끊어 원인이 가려진다.
      timeout: '60s',
    }
  );
  if (record(res, [200])) {
    try {
      candidateCount.add(res.json('data').length);
    } catch (_) {
      // 응답 형태가 다르면 후보 수는 안 센다. 여기서 테스트를 죽일 이유는 없다.
    }
  }
}

function createPlace(kakaoPlaceId, name, tag) {
  return http.post(
    `${PLACE}/api/v1/places`,
    JSON.stringify({
      name,
      address: '서울 성동구 성수동2가 277-135',
      roadAddress: '서울 성동구 아차산로9길 8',
      lat: 37.544782,
      lng: 127.058208,
      category: '음식점 > 카페',
      kakaoPlaceId,
      sourceType: 'INSTAGRAM',
      sourceUrl: 'https://www.instagram.com/reel/perf-test/',
      mapId: MAP_ID,
    }),
    {
      headers: { 'Content-Type': 'application/json', 'X-User-Id': String(OWNER_ID) },
      tags: { name: tag },
    }
  );
}

// ── 시나리오 2: 등록만 (매번 다른 장소) ───────────────────────────────────────
export function register() {
  // VU·반복 번호를 섞어 매번 새 장소를 만든다 — 중복 409에 막히지 않고 순수 쓰기 경로만 잰다.
  const id = `k6-${__VU}-${__ITER}`;
  record(createPlace(id, `k6 장소 ${id}`, 'register'), [200, 201]);
}

// ── 시나리오 3: 같은 장소 동시 등록 (중복 race) ───────────────────────────────
export function race() {
  // 초 단위로 키를 묶는다 — 같은 1초 안에 도는 모든 VU가 같은 장소를 동시에 등록하려 든다.
  const id = `k6-race-${Math.floor(Date.now() / 1000)}`;
  // 기대: 최초 1건만 201, 나머지는 409(DUPLICATE_PLACE). 500이 나오면 유니크 제약 위반이 새는 것.
  record(createPlace(id, `k6 중복장소 ${id}`, 'race'), [200, 201, 409]);
}
