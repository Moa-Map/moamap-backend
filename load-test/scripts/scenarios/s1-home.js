// S1 — 홈 진입
//
// 앱을 켰을 때 홈에서 실제로 불리는 조회를 묶는다. 전부 map-service 안에서 끝나므로
// 서비스 간 홉 비용이 섞이지 않는다 — 지도 목록 자체의 비용만 분리해서 잰다.
//
//   GET /maps?sort=POPULAR   커뮤니티 지도 목록. tags 가 @ElementCollection(지연 로딩)이라
//                            페이지 건수만큼 태그 쿼리가 따라붙는지(N+1) 보는 자리.
//   GET /maps/official       공식 지도 목록.
//   GET /maps/recommendations 유일한 CPU 바운드 경로. 외부 호출 없이 JVM 안에서 점수를 매긴다.
//
// 목록은 얕은 페이지와 깊은 페이지를 절반씩 본다. offset 페이지네이션은 뒤로 갈수록
// DB가 앞의 행을 전부 읽고 버리기 때문에, 나눠서 재지 않으면 그 비용이 평균에 묻힌다.
//
// 실행:
//   k6 run -e MODE=smoke  load-test/scripts/scenarios/s1-home.js
//   k6 run -e MODE=load -e TARGET_RPS=50 load-test/scripts/scenarios/s1-home.js

import http from 'k6/http';
import { sleep } from 'k6';
import {
    urlOf,
    SEED,
    PAGE_SIZE,
    randomId,
    lastPageOf,
    pickPage,
    scenarioOptions,
} from '../lib/config.js';
import { authHeaders } from '../lib/auth.js';
import { checkApiOk, nonEmptyList } from '../lib/checks.js';

export const options = scenarioOptions('s1_home', [
    'GET /maps?sort=POPULAR (shallow)',
    'GET /maps?sort=POPULAR (deep)',
    'GET /maps/official',
    'GET /maps/recommendations',
]);

const MAPS_LAST_PAGE = lastPageOf(SEED.communityMapCount);

export default function () {
    // 매 반복 다른 유저를 골라 캐시나 특정 행에 쏠리지 않게 한다.
    const params = authHeaders(randomId(SEED.userCount));
    const map = urlOf('map');
    const list = pickPage(MAPS_LAST_PAGE);

    // 홈 화면에서도 세 목록이 동시에 불린다. 병렬로 보낸다.
    const responses = http.batch([
        {
            method: 'GET',
            url: `${map}/api/v1/maps?sort=POPULAR&page=${list.page}&size=${PAGE_SIZE}`,
            // page 번호가 URL에 들어가므로 태그로 묶지 않으면 지표가 요청마다 쪼개진다.
            // 다만 얕은/깊은은 비용이 다르므로 그 축만 남긴다.
            params: { ...params, tags: { name: `GET /maps?sort=POPULAR (${list.depth})` } },
        },
        {
            method: 'GET',
            url: `${map}/api/v1/maps/official?page=0&size=${PAGE_SIZE}`,
            params: { ...params, tags: { name: 'GET /maps/official' } },
        },
        {
            method: 'GET',
            url: `${map}/api/v1/maps/recommendations?size=10`,
            params: { ...params, tags: { name: 'GET /maps/recommendations' } },
        },
    ]);

    // 목록이 비어 있으면 시드가 안 들어간 것이다. 빈 응답은 항상 빠르므로
    // 그대로 두면 "빈 응답을 빠르게 반환하는 것"을 측정하게 된다.
    checkApiOk(responses[0], '커뮤니티 지도 목록', nonEmptyList);
    checkApiOk(responses[1], '공식 지도 목록');
    checkApiOk(responses[2], '추천 지도');

    sleep(1);
}
