// k6 부하 테스트 공통 설정.
//
// 주소·시크릿은 전부 환경변수로만 받는다. 이 레포는 퍼블릭이라 어떤 실제 값도 코드에 두지 않는다.
// 값은 load-test/.env.loadtest 에 두고 실행 시 -e 로 넘긴다(해당 파일은 gitignore 대상).

// ── 측정 경로 ────────────────────────────────────────────────────────────────
// gateway : 게이트웨이(8080)를 통과한다. JWT를 직접 서명해 보낸다.
//           사용자가 실제로 겪는 end-to-end 수치라 "우리 서비스 p95"는 이쪽이다.
// direct  : 서비스를 직접 때린다(8081~8083). 인증은 X-User-Id 헤더로 대체된다.
//           프록시 구간이 빠져 측정 대상이 서비스 자체로 좁혀진다 — 병목 사냥용.
//
// 하나를 고르는 게 아니라 회차마다 둘 다 잰다. 두 수치의 차이가 곧 게이트웨이 비용이다.
export const TARGET = __ENV.TARGET || 'gateway';

// 오타를 반복 중에 발견하면 매 이터레이션마다 같은 예외가 쏟아진다. 로드 시점에 한 번만 터뜨린다.
if (TARGET !== 'gateway' && TARGET !== 'direct') {
    throw new Error(`알 수 없는 TARGET: ${TARGET} (gateway|direct)`);
}

const GATEWAY_URL = __ENV.BASE_URL || 'http://localhost:8080';
const DIRECT_URL = {
    map: __ENV.MAP_URL || 'http://localhost:8083',
    place: __ENV.PLACE_URL || 'http://localhost:8082',
    user: __ENV.USER_URL || 'http://localhost:8081',
};

/** 서비스 이름을 현재 TARGET 에 맞는 base URL 로 바꾼다. */
export function urlOf(service) {
    if (TARGET === 'gateway') {
        return GATEWAY_URL;
    }
    const url = DIRECT_URL[service];
    if (!url) {
        throw new Error(`알 수 없는 서비스: ${service} (map|place|user)`);
    }
    return url;
}

// ── 시드 데이터 규모 ─────────────────────────────────────────────────────────
// load-test/seed/seed.sql 과 반드시 일치해야 한다. 어긋나면 없는 id를 조회해
// 404·빈 응답만 재게 되는데, 그건 항상 빠르므로 "성능이 좋다"로 착각하게 된다.
export const SEED = {
    userCount: Number(__ENV.SEED_USER_COUNT || 5000),
    mapCount: Number(__ENV.SEED_MAP_COUNT || 7000),
    // 지도 7000개 중 5/7이 COMMUNITY. GET /api/v1/maps 목록에 잡히는 건 이쪽뿐이다.
    communityMapCount: Number(__ENV.SEED_COMMUNITY_MAP_COUNT || 5000),
    membersPerMap: Number(__ENV.SEED_MEMBERS_PER_MAP || 6),

    // 장소를 몰아넣은 지도. 일반 지도는 장소 6만 개를 7천 개에 흩뿌려서 지도당 8.5개뿐이라
    // "장소가 많을 때 느려진다"를 잴 수 없다. seed.sql이 이 둘만 따로 심는다.
    hotMapId: 900001, // COMMUNITY, 장소 6000, 멤버 60
    personalMapId: 900002, // PRIVATE(personal), 장소 6000, 멤버는 소유자 1명뿐
    personalOwnerId: 1,
    hotPlaceCount: 6000,
    hotMemberCount: 60,
};

export const PAGE_SIZE = 20;

/** 시드 범위 안에서 임의의 id를 고른다. id는 1부터 시작한다고 가정한다. */
export function randomId(max) {
    return Math.floor(Math.random() * max) + 1;
}

// ── 멤버십 ───────────────────────────────────────────────────────────────────
// 지도 조회·장소 등록은 "그 지도의 멤버냐"를 검사한다(place-service → map-service getMemberInfo).
// userId 와 mapId 를 각각 랜덤으로 뽑으면 거의 항상 403이 나서 인가 실패 경로만 재게 된다.
// seed.sql 의 멤버십 산식이 결정적이므로 여기서 그대로 재현해 "진짜 멤버"를 뽑는다.

/**
 * 해당 지도의 멤버 한 명을 고른다.
 *
 * seed.sql: user_id = ((m.id * 7 + k * 13) % :users) + 1, k = 1..:members_per_map
 * 그 중 owner 와 겹치는 k는 INSERT에서 빠지지만, 겹쳤다는 건 곧 owner라는 뜻이고
 * owner 도 멤버이므로 어느 쪽이든 결과는 실제 멤버다.
 */
export function memberOf(mapId) {
    // 개인 지도는 소유자만 멤버다.
    if (mapId === SEED.personalMapId) {
        return SEED.personalOwnerId;
    }
    // 몰린 지도는 seed.sql 6-1) 이 따로 심는다: user_id = ((k * 37) % :users) + 1, k = 1..:hot_members
    if (mapId === SEED.hotMapId) {
        const k = 1 + Math.floor(Math.random() * SEED.hotMemberCount);
        return ((k * 37) % SEED.userCount) + 1;
    }
    const k = 1 + Math.floor(Math.random() * SEED.membersPerMap);
    return ((mapId * 7 + k * 13) % SEED.userCount) + 1;
}

// ── 페이지 깊이 ──────────────────────────────────────────────────────────────
// offset 페이지네이션은 뒤로 갈수록 DB가 앞의 행을 전부 읽고 버린다.
// 얕은/깊은 구간을 태그로 나눠 재야 그 비용이 수치로 드러난다.
const SHALLOW_MAX_PAGE = 4;

/** 전체 행 수로 마지막 페이지 번호를 구한다. */
export function lastPageOf(rowCount) {
    return Math.max(0, Math.floor(rowCount / PAGE_SIZE) - 1);
}

/**
 * 얕은/깊은 페이지를 절반씩 고른다.
 * 실제 사용자는 앞쪽을 훨씬 많이 보지만, 여기서는 깊은 페이지 비용을 재는 게 목적이라 50:50으로 둔다.
 */
export function pickPage(lastPage) {
    if (lastPage <= SHALLOW_MAX_PAGE) {
        return { page: Math.floor(Math.random() * (lastPage + 1)), depth: 'shallow' };
    }
    if (Math.random() < 0.5) {
        return { page: Math.floor(Math.random() * (SHALLOW_MAX_PAGE + 1)), depth: 'shallow' };
    }
    const span = lastPage - SHALLOW_MAX_PAGE;
    return { page: SHALLOW_MAX_PAGE + 1 + Math.floor(Math.random() * span), depth: 'deep' };
}

// ── 시크릿 ───────────────────────────────────────────────────────────────────

// jjwt(Keys.hmacShaKeyFor)가 HS256에 요구하는 최소 키 길이.
const MIN_SECRET_LENGTH = 32;

/**
 * 테스트 전용 JWT 시크릿을 읽는다. 없거나 짧으면 즉시 중단한다.
 *
 * 기본값을 두지 않는 이유: 기본값이 있으면 시크릿을 안 넘긴 채로 실행돼도 그럭저럭 돌아가버려서,
 * 나중에 그 값이 코드에 박힌 진짜 시크릿으로 바뀌는 사고가 난다. 없으면 없다고 터지는 편이 안전하다.
 *
 * TARGET=direct 는 게이트웨이를 안 거치므로 호출되지 않는다.
 */
export function requireSecret() {
    const secret = __ENV.JWT_SECRET;
    if (!secret) {
        throw new Error(
            'JWT_SECRET 이 없습니다. load-test/.env.loadtest 를 확인하세요. (운영 시크릿은 절대 사용 금지)'
        );
    }
    if (secret.length < MIN_SECRET_LENGTH) {
        throw new Error(
            `JWT_SECRET 이 너무 짧습니다(${secret.length}자). HS256은 ${MIN_SECRET_LENGTH}자 이상이 필요합니다.`
        );
    }
    return secret;
}

// ── 부하 모양 ────────────────────────────────────────────────────────────────

// 목표 부하. 탐색 램프(probe)로 knee를 구한 뒤 그 값을 기준으로 채운다.
const TARGET_RPS = Number(__ENV.TARGET_RPS || 0);

function requireTargetRps(mode) {
    if (!TARGET_RPS) {
        throw new Error(
            `MODE=${mode} 는 TARGET_RPS 가 필요합니다. 먼저 MODE=probe 로 knee 를 구한 뒤 -e TARGET_RPS=<값> 으로 넘기세요.`
        );
    }
    return TARGET_RPS;
}

// 1차 실행용 임계값. 아직 기준 수치가 없으므로 "명백한 고장"만 잡는다.
// 베이스라인이 나오면 실측값으로 조여서 성능 회귀 게이트로 쓴다.
const DEFAULT_THRESHOLDS = {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
    // 200인데 빈 응답이면 여기서 걸린다. checks.js 가 데이터 유무까지 보기 때문.
    checks: ['rate>0.99'],
    // k6가 목표 부하를 못 낸 회차는 결과가 무효다. 0이 아니면 폐기하고 재실행한다.
    dropped_iterations: ['count<1'],
};

/**
 * 요청 태그별 p95를 요약에 찍기 위한 threshold.
 *
 * k6는 threshold가 걸린 태그만 요약에 따로 출력한다. 합격/불합격 판정이 목적이 아니므로
 * 절대 실패하지 않는 조건(p(95)>=0)을 건다. 이게 없으면 시나리오별 p95를 볼 수 없다.
 */
function outputThresholds(requestNames) {
    const out = {};
    for (const name of requestNames) {
        out[`http_req_duration{name:${name}}`] = ['p(95)>=0'];
    }
    return out;
}

/**
 * MODE 환경변수에 따라 부하 모양을 만든다.
 *
 * 전부 arrival-rate(open model)를 쓴다. VU 기반(closed model)은 서버가 느려지면 부하도 같이 줄어들어,
 * 리팩터링 전후를 비교할 때 "개선했는데 처리량은 그대로"인 것처럼 보인다.
 *
 * @param scenarioName k6 시나리오 이름
 * @param requestNames 요약에 p95를 찍을 요청 태그 목록
 */
export function scenarioOptions(scenarioName, requestNames = []) {
    const mode = __ENV.MODE || 'smoke';
    return {
        thresholds: { ...DEFAULT_THRESHOLDS, ...outputThresholds(requestNames) },
        scenarios: { [scenarioName]: executorFor(mode) },
        discardResponseBodies: false,
    };
}

function executorFor(mode) {
    switch (mode) {
        // 부하 측정이 아니라 스크립트 검증용. 응답에 실제 데이터가 오는지 눈으로 본다.
        case 'smoke':
            return { executor: 'constant-vus', vus: 1, duration: '1m' };

        // 한계치(knee) 탐색. p95가 급격히 꺾이는 지점을 찾아 이후 모든 부하량의 기준으로 삼는다.
        case 'probe':
            return {
                executor: 'ramping-arrival-rate',
                startRate: 1,
                timeUnit: '1s',
                preAllocatedVUs: 50,
                maxVUs: 500,
                stages: [{ target: 200, duration: '5m' }],
            };

        // 베이스라인. 앞 2분은 JIT 워밍업 구간이라 집계에서 제외하고 읽는다.
        case 'load':
            return {
                executor: 'constant-arrival-rate',
                rate: requireTargetRps(mode),
                timeUnit: '1s',
                duration: '10m',
                preAllocatedVUs: 100,
                maxVUs: 500,
            };

        // 병목 찾기. 마지막 회복 구간이 핵심 — 부하를 뺐는데도 안 돌아오면 훨씬 심각한 문제다.
        case 'stress':
            return {
                executor: 'ramping-arrival-rate',
                startRate: 1,
                timeUnit: '1s',
                preAllocatedVUs: 100,
                maxVUs: 1000,
                stages: [
                    { target: Math.round(requireTargetRps(mode)), duration: '3m' },
                    { target: Math.round(requireTargetRps(mode) * 3), duration: '5m' },
                    { target: Math.round(requireTargetRps(mode) * 3), duration: '5m' },
                    { target: 0, duration: '2m' },
                ],
            };

        // 급증 회복력. 판정은 "버텼나"가 아니라 "원래 성능으로 돌아왔나"다.
        case 'spike':
            return {
                executor: 'ramping-arrival-rate',
                startRate: 1,
                timeUnit: '1s',
                preAllocatedVUs: 100,
                maxVUs: 1000,
                stages: [
                    { target: Math.round(requireTargetRps(mode)), duration: '2m' },
                    { target: Math.round(requireTargetRps(mode) * 4), duration: '10s' },
                    { target: Math.round(requireTargetRps(mode) * 4), duration: '1m' },
                    { target: Math.round(requireTargetRps(mode)), duration: '10s' },
                    { target: Math.round(requireTargetRps(mode)), duration: '3m' },
                ],
            };

        // 지속. 세게가 아니라 오래가 목적이다. 절대값이 아니라 기울기(p95 추세, 힙 바닥)를 본다.
        case 'soak':
            return {
                executor: 'constant-arrival-rate',
                rate: requireTargetRps(mode),
                timeUnit: '1s',
                duration: '30m',
                preAllocatedVUs: 100,
                maxVUs: 500,
            };

        default:
            throw new Error(`알 수 없는 MODE: ${mode} (smoke|probe|load|stress|spike|soak)`);
    }
}
