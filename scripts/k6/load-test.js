// 로컬 읽기 API 부하 테스트. before/after 비교가 목적이라 절대치는 의미 없다.
//   brew install k6
//   k6 run scripts/k6/load-test.js
//   VUS/DURATION 환경변수로 부하량 조절 (기본 20VU 1분)
//
// 게이트웨이(8080)를 거치지 않고 서비스를 직접 때린다 — JWT 발급이 필요 없고,
// 프록시 구간이 빠져 측정 대상이 서비스 자체로 좁혀진다. 인증은 X-User-Id 헤더로 대체된다.
//
// threshold는 합격 기준이 아니라 출력용이다. baseline을 먼저 뽑고 목표치는 그 숫자를 보고 정한다.
import http from 'k6/http';
import { check } from 'k6';

const MAP = __ENV.MAP_URL || 'http://localhost:8083';
const PLACE = __ENV.PLACE_URL || 'http://localhost:8082';

// 아래 값들은 scripts/perf-seed/seed.sql 과 맞춰야 한다. 어긋나면 없는 id를 때리거나
// 존재하지 않는 페이지만 봐서(빈 결과라 항상 빠르다) 측정이 무의미해진다.
const USERS = 5000;
const HOT_MAP = 900001; // 커뮤니티 지도, 장소 6000개
const PERSONAL_MAP = 900002; // 개인(PRIVATE) 지도, 장소 6000개, 소유자 userId=1
const PERSONAL_OWNER = 1;
const PAGE_SIZE = 20;

// 마지막 페이지 번호. 커뮤니티 지도는 5000개(seed.sql에서 7000개 중 5/7).
const LAST_PAGE = {
  maps: Math.floor(5000 / PAGE_SIZE) - 1, // 249
  places: Math.floor(6000 / PAGE_SIZE) - 1, // 299
};
// 이 페이지 번호까지를 "얕은 페이지"로 본다. offset 페이지네이션은 뒤로 갈수록
// DB가 앞의 행을 전부 읽고 버리기 때문에, 얕은/깊은 구간을 나눠 재야 그 비용이 드러난다.
const SHALLOW_MAX = 4;

export const options = {
  stages: [
    { duration: '30s', target: Number(__ENV.VUS || 20) },
    { duration: __ENV.DURATION || '1m', target: Number(__ENV.VUS || 20) },
    { duration: '20s', target: 0 },
  ],
  // 합격/불합격 기준이 아니라 출력용이다. k6는 threshold가 걸린 태그만 요약에 따로 찍어주기 때문에,
  // 시나리오별 p95를 보려고 절대 실패하지 않는 조건(p(95)>=0)을 건다.
  thresholds: Object.fromEntries(
    [
      'maps_list',
      'places_community',
      'places_personal',
    ].flatMap((s) => [
      [`http_req_duration{name:${s}_shallow}`, ['p(95)>=0']],
      [`http_req_duration{name:${s}_deep}`, ['p(95)>=0']],
    ])
  ),
};

// 절반은 얕은 페이지, 절반은 깊은 페이지를 본다.
// 실제 사용자도 앞쪽을 훨씬 많이 보지만, 여기서는 깊은 페이지 비용을 재는 게 목적이라 50:50으로 둔다.
function pickPage(lastPage) {
  const deep = Math.random() < 0.5;
  return deep
    ? { page: SHALLOW_MAX + 1 + Math.floor(Math.random() * (lastPage - SHALLOW_MAX)), suffix: 'deep' }
    : { page: Math.floor(Math.random() * (SHALLOW_MAX + 1)), suffix: 'shallow' };
}

export default function () {
  // 매 반복 다른 유저를 골라 캐시나 특정 행에 쏠리지 않게 한다.
  const userId = 1 + Math.floor(Math.random() * USERS);

  const maps = pickPage(LAST_PAGE.maps);
  const community = pickPage(LAST_PAGE.places);
  const personal = pickPage(LAST_PAGE.places);

  const requests = [
    // 시나리오 1: 지도가 많을 때 커뮤니티 지도 목록
    [
      `maps_list_${maps.suffix}`,
      `${MAP}/api/v1/maps?sort=POPULAR&page=${maps.page}&size=${PAGE_SIZE}`,
      userId,
    ],
    // 시나리오 2: 커뮤니티 지도 하나에 장소가 많을 때
    [
      `places_community_${community.suffix}`,
      `${PLACE}/api/v1/places?mapId=${HOT_MAP}&page=${community.page}&size=${PAGE_SIZE}`,
      userId,
    ],
    // 시나리오 3: 내 개인 지도에 장소가 많을 때. 소유자 본인으로 호출한다.
    [
      `places_personal_${personal.suffix}`,
      `${PLACE}/api/v1/places?mapId=${PERSONAL_MAP}&page=${personal.page}&size=${PAGE_SIZE}`,
      PERSONAL_OWNER,
    ],
  ];

  const res = http.batch(
    requests.map(([name, url, uid]) => ({
      method: 'GET',
      url,
      params: { headers: { 'X-User-Id': String(uid) }, tags: { name } },
    }))
  );
  // 에러 응답은 빠르다 — 이 검사가 100%가 아니면 아래 숫자는 전부 무효다.
  res.forEach((r) => check(r, { 'status 200': (x) => x.status === 200 }));
}
