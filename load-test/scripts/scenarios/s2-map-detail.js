// S2 — 지도 상세 진입 (대표 시나리오)
//
// 지도를 눌러 상세로 들어가는 흐름을 흉내 낸다. 서비스 간 홉 3종을 전부 지나는 유일한 경로라
// 병목이 가장 잘 드러난다.
//
//   GET /maps/{id}            map-service       (단독)
//   GET /places?mapId=        place-service  →  map-service  (getMemberInfo 홉)
//   GET /maps/{id}/members    map-service    →  user-service (findProfiles 홉)
//
// 대상 지도는 seed.sql이 심어둔 "장소가 몰린 지도"(900001)다. 일반 지도는 장소가 8~9개뿐이라
// 페이지 1장으로 끝나서 목록 조회 비용이 드러나지 않는다. 장소 6000개짜리 지도에서
// 얕은 페이지와 깊은 페이지를 절반씩 봐야 offset 페이지네이션 비용이 수치로 나온다.
//
// 실행:
//   k6 run -e MODE=smoke  load-test/scripts/scenarios/s2-map-detail.js          # 스크립트 검증
//   k6 run -e MODE=probe  load-test/scripts/scenarios/s2-map-detail.js          # knee 탐색
//   k6 run -e MODE=load -e TARGET_RPS=50 load-test/scripts/scenarios/s2-map-detail.js
//   k6 run -e TARGET=direct -e MODE=load -e TARGET_RPS=50 ...                   # 게이트웨이 제외

import http from 'k6/http';
import { group, sleep } from 'k6';
import {
    urlOf,
    SEED,
    PAGE_SIZE,
    memberOf,
    lastPageOf,
    pickPage,
    scenarioOptions,
} from '../lib/config.js';
import { authHeaders } from '../lib/auth.js';
import { checkApiOk, nonEmptyList } from '../lib/checks.js';

export const options = scenarioOptions('s2_map_detail', [
    'GET /maps/:id',
    'GET /places?mapId (shallow)',
    'GET /places?mapId (deep)',
    'GET /maps/:id/members',
]);

const MAP_ID = SEED.hotMapId;
const PLACES_LAST_PAGE = lastPageOf(SEED.hotPlaceCount);

export default function () {
    // 조회는 그 지도의 멤버여야 통과한다. userId 를 따로 랜덤으로 뽑으면 403만 재게 된다.
    const params = authHeaders(memberOf(MAP_ID));
    const map = urlOf('map');
    const place = urlOf('place');
    const places = pickPage(PLACES_LAST_PAGE);

    group('지도 상세 진입', function () {
        // 1) 상세를 먼저 받는다. 화면에서도 이게 와야 나머지를 부를 수 있다.
        //    id가 URL에 들어가므로 tags.name 으로 묶지 않으면 지표가 요청마다 쪼개진다.
        const detail = http.get(`${map}/api/v1/maps/${MAP_ID}`, {
            ...params,
            tags: { name: 'GET /maps/:id' },
        });
        checkApiOk(detail, '지도 상세');

        // 2) 장소 목록과 멤버 목록은 실제 화면에서도 동시에 불린다. 병렬로 보낸다.
        const responses = http.batch([
            {
                method: 'GET',
                url: `${place}/api/v1/places?mapId=${MAP_ID}&page=${places.page}&size=${PAGE_SIZE}`,
                params: { ...params, tags: { name: `GET /places?mapId (${places.depth})` } },
            },
            {
                method: 'GET',
                url: `${map}/api/v1/maps/${MAP_ID}/members`,
                params: { ...params, tags: { name: 'GET /maps/:id/members' } },
            },
        ]);

        // 목록이 비어 있으면 시드가 안 들어간 것이다. 그대로 두면 측정이 무의미해진다.
        checkApiOk(responses[0], '장소 목록', nonEmptyList);
        checkApiOk(responses[1], '멤버 목록', (data) => data && data.memberCount > 0);
    });

    // 사용자가 화면을 보는 시간. arrival-rate 모델에서는 부하량에 영향을 주지 않고
    // VU가 재사용되는 속도만 바꾼다.
    sleep(1);
}
