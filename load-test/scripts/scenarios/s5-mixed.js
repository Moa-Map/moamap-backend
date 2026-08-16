// S5 — 혼합 (읽기 7 : 쓰기 3)
//
// 읽기 전용으로만 재면 락 경합과 커넥션 경쟁이 보이지 않는다. 쓰기를 섞어 실제 트래픽에 가깝게 만든다.
// 비율은 논의 대상이다(README §10-2). 실제 사용 패턴에 대한 감이 있으면 조정한다.
//
// 주의 1: 쓰기가 섞이므로 돌릴 때마다 place 행이 늘어난다. soak(30분)으로 돌리면 상당히 쌓이므로,
//         회차 사이에 시드를 다시 적재하거나 증가분을 기록해 둔다.
// 주의 2: POST /places 는 해당 지도의 멤버여야 통과한다(place-service → map-service getMemberInfo).
//         지금은 userId/mapId 를 독립적으로 뽑고 있어 멤버가 아니면 403이 난다.
//         → TODO: 시드에서 만든 (userId, mapId) 멤버십 쌍을 그대로 쓰도록 맞춰야 한다.
//
// 실행:
//   k6 run -e MODE=smoke scenarios/s5-mixed.js
//   k6 run -e MODE=load -e TARGET_RPS=50 scenarios/s5-mixed.js

import http from 'k6/http';
import { sleep } from 'k6';
import { BASE_URL, SEED, randomId, requireSecret, scenarioOptions } from '../lib/config.js';
import { authHeaders } from '../lib/auth.js';
import { checkApiOk, checkApiCreated, nonEmptyList } from '../lib/checks.js';

const SECRET = requireSecret();
const WRITE_RATIO = 0.3;

export const options = scenarioOptions('s5_mixed');

export default function () {
    const userId = randomId(SEED.userCount);
    const mapId = randomId(SEED.mapCount);
    const params = authHeaders(userId, SECRET);

    if (Math.random() < WRITE_RATIO) {
        createPlace(mapId, params);
    } else {
        readSomething(userId, mapId, params);
    }

    sleep(1);
}

function readSomething(userId, mapId, params) {
    // 홈 화면과 상세 화면에서 실제로 불리는 조회들을 고르게 섞는다.
    const pick = Math.floor(Math.random() * 4);
    switch (pick) {
        case 0:
            checkApiOk(
                http.get(`${BASE_URL}/api/v1/maps/official?page=0&size=20`, {
                    ...params,
                    tags: { name: 'GET /maps/official' },
                }),
                '공식 지도 목록',
                nonEmptyList
            );
            break;
        case 1:
            checkApiOk(
                http.get(`${BASE_URL}/api/v1/maps/${mapId}`, {
                    ...params,
                    tags: { name: 'GET /maps/:id' },
                }),
                '지도 상세'
            );
            break;
        case 2:
            checkApiOk(
                http.get(`${BASE_URL}/api/v1/places?mapId=${mapId}&page=0&size=20`, {
                    ...params,
                    tags: { name: 'GET /places?mapId' },
                }),
                '장소 목록',
                nonEmptyList
            );
            break;
        default:
            // 유일한 CPU 바운드 엔드포인트. 외부 호출 없이 JVM 안에서 점수를 매긴다.
            checkApiOk(
                http.get(`${BASE_URL}/api/v1/maps/recommendations?size=10`, {
                    ...params,
                    tags: { name: 'GET /maps/recommendations' },
                }),
                '추천 지도'
            );
    }
}

function createPlace(mapId, params) {
    // kakaoPlaceId 는 중복 판정에 쓰이므로 매번 다른 값을 만든다.
    const unique = `${__VU}-${__ITER}-${Date.now()}`;
    const payload = JSON.stringify({
        name: `부하테스트 장소 ${unique}`,
        address: '서울특별시 중구 세종대로 110',
        roadAddress: '서울특별시 중구 세종대로 110',
        lat: 37.5665,
        lng: 126.978,
        category: '카페',
        kakaoPlaceId: `loadtest-${unique}`,
        sourceType: 'KAKAO_SEARCH',
        description: '부하 테스트로 생성된 장소',
        mapId: mapId,
        tags: ['부하테스트'],
    });

    checkApiCreated(
        http.post(`${BASE_URL}/api/v1/places`, payload, {
            ...params,
            tags: { name: 'POST /places' },
        }),
        '장소 등록'
    );
}
