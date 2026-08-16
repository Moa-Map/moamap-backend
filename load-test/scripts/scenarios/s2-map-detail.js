// S2 — 지도 상세 진입 (대표 시나리오)
//
// 지도를 눌러 상세로 들어가는 흐름을 흉내 낸다. 서비스 간 홉 3종을 전부 지나는 유일한 경로라
// 병목이 가장 잘 드러난다.
//
//   GET /maps/{id}            map-service       (단독)
//   GET /places?mapId=        place-service  →  map-service  (getMemberInfo 홉)
//   GET /maps/{id}/members    map-service    →  user-service (findProfiles 홉)
//
// 실행:
//   k6 run -e MODE=smoke  scenarios/s2-map-detail.js          # 스크립트 검증
//   k6 run -e MODE=probe  scenarios/s2-map-detail.js          # knee 탐색
//   k6 run -e MODE=load -e TARGET_RPS=50 scenarios/s2-map-detail.js

import http from 'k6/http';
import { group, sleep } from 'k6';
import { BASE_URL, SEED, randomId, requireSecret, scenarioOptions } from '../lib/config.js';
import { authHeaders } from '../lib/auth.js';
import { checkApiOk, nonEmptyList } from '../lib/checks.js';

const SECRET = requireSecret();

export const options = scenarioOptions('s2_map_detail');

export default function () {
    const userId = randomId(SEED.userCount);
    const mapId = randomId(SEED.mapCount);
    const params = authHeaders(userId, SECRET);

    group('지도 상세 진입', function () {
        // 1) 상세를 먼저 받는다. 화면에서도 이게 와야 나머지를 부를 수 있다.
        //    id가 URL에 들어가므로 tags.name 으로 묶지 않으면 지표가 요청마다 쪼개진다.
        const detail = http.get(`${BASE_URL}/api/v1/maps/${mapId}`, {
            ...params,
            tags: { name: 'GET /maps/:id' },
        });
        checkApiOk(detail, '지도 상세');

        // 2) 장소 목록과 멤버 목록은 실제 화면에서도 동시에 불린다. 병렬로 보낸다.
        const responses = http.batch([
            {
                method: 'GET',
                url: `${BASE_URL}/api/v1/places?mapId=${mapId}&page=0&size=20`,
                params: { ...params, tags: { name: 'GET /places?mapId' } },
            },
            {
                method: 'GET',
                url: `${BASE_URL}/api/v1/maps/${mapId}/members`,
                params: { ...params, tags: { name: 'GET /maps/:id/members' } },
            },
        ]);

        // Smoke 단계에서 목록이 비어 있으면 시드가 안 들어간 것이다. 그대로 두면 측정이 무의미해진다.
        checkApiOk(responses[0], '장소 목록', nonEmptyList);
        checkApiOk(responses[1], '멤버 목록', (data) => data && data.memberCount > 0);
    });

    // 사용자가 화면을 보는 시간. arrival-rate 모델에서는 부하량에 영향을 주지 않고
    // VU가 재사용되는 속도만 바꾼다.
    sleep(1);
}
