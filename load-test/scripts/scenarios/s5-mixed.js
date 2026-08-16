// S5 — 혼합 (읽기 7 : 쓰기 3)
//
// 읽기 전용으로만 재면 락 경합과 커넥션 경쟁이 보이지 않는다. 쓰기를 섞어 실제 트래픽에 가깝게 만든다.
// 비율은 논의 대상이다(README §10-2). 실제 사용 패턴에 대한 감이 있으면 조정한다.
//
// 조회·등록 모두 "그 지도의 멤버냐"를 검사한다(place-service → map-service getMemberInfo).
// 그래서 mapId 를 먼저 뽑고, 그 지도의 실제 멤버를 config.memberOf() 로 뽑는다.
// userId 와 mapId 를 각각 독립적으로 뽑으면 거의 전부 403이 나서 인가 실패 경로만 재게 된다.
//
// ⚠️ 쓰기가 섞이므로 돌릴 때마다 place 행이 늘어난다. soak(30분)이면 상당히 쌓인다.
//    회차 사이 정리:
//      DELETE FROM place_service.places WHERE kakao_place_id LIKE 'loadtest-%';
//    또는 seed.sql 을 다시 적재한다(TRUNCATE 포함이라 재실행 안전).
//
// 실행:
//   k6 run -e MODE=smoke load-test/scripts/scenarios/s5-mixed.js
//   k6 run -e MODE=load -e TARGET_RPS=50 load-test/scripts/scenarios/s5-mixed.js

import http from 'k6/http';
import { sleep } from 'k6';
import { urlOf, SEED, PAGE_SIZE, randomId, memberOf, scenarioOptions } from '../lib/config.js';
import { authHeaders } from '../lib/auth.js';
import { checkApiOk, checkApiCreated, nonEmptyList } from '../lib/checks.js';

const WRITE_RATIO = 0.3;

export const options = scenarioOptions('s5_mixed', [
    'GET /maps/official',
    'GET /maps/:id',
    'GET /places?mapId',
    'GET /maps/recommendations',
    'POST /places',
]);

export default function () {
    const mapId = randomId(SEED.mapCount);
    const params = authHeaders(memberOf(mapId));

    if (Math.random() < WRITE_RATIO) {
        createPlace(mapId, params);
    } else {
        readSomething(mapId, params);
    }

    sleep(1);
}

function readSomething(mapId, params) {
    // 홈 화면과 상세 화면에서 실제로 불리는 조회들을 고르게 섞는다.
    const map = urlOf('map');
    const pick = Math.floor(Math.random() * 4);
    switch (pick) {
        case 0:
            checkApiOk(
                http.get(`${map}/api/v1/maps/official?page=0&size=${PAGE_SIZE}`, {
                    ...params,
                    tags: { name: 'GET /maps/official' },
                }),
                '공식 지도 목록',
                nonEmptyList
            );
            break;
        case 1:
            checkApiOk(
                http.get(`${map}/api/v1/maps/${mapId}`, {
                    ...params,
                    tags: { name: 'GET /maps/:id' },
                }),
                '지도 상세'
            );
            break;
        case 2:
            // 일반 지도는 장소가 8~9개뿐이라 항상 첫 페이지다. 깊은 페이지 비용은 S2에서 잰다.
            checkApiOk(
                http.get(
                    `${urlOf('place')}/api/v1/places?mapId=${mapId}&page=0&size=${PAGE_SIZE}`,
                    { ...params, tags: { name: 'GET /places?mapId' } }
                ),
                '장소 목록',
                nonEmptyList
            );
            break;
        default:
            // 유일한 CPU 바운드 엔드포인트. 외부 호출 없이 JVM 안에서 점수를 매긴다.
            checkApiOk(
                http.get(`${map}/api/v1/maps/recommendations?size=10`, {
                    ...params,
                    tags: { name: 'GET /maps/recommendations' },
                }),
                '추천 지도'
            );
    }
}

function createPlace(mapId, params) {
    // kakaoPlaceId 는 중복 판정에 쓰이므로 매번 다른 값을 만든다.
    // 접두사 loadtest- 는 회차 사이 정리 쿼리가 잡을 수 있게 고정한다.
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
        http.post(`${urlOf('place')}/api/v1/places`, payload, {
            ...params,
            tags: { name: 'POST /places' },
        }),
        '장소 등록'
    );
}
