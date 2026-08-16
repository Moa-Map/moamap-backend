// k6 부하 테스트 공통 설정.
//
// 주소·시크릿은 전부 환경변수로만 받는다. 이 레포는 퍼블릭이라 어떤 실제 값도 코드에 두지 않는다.
// 값은 load-test/.env.loadtest 에 두고 실행 시 -e 로 넘긴다(해당 파일은 gitignore 대상).

// jjwt(Keys.hmacShaKeyFor)가 HS256에 요구하는 최소 키 길이.
const MIN_SECRET_LENGTH = 32;

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 시드로 넣은 데이터 규모. 스크립트가 조회할 id 범위를 여기서 정한다.
// 시드 SQL을 바꾸면 이 값도 같이 바꿔야 한다(안 그러면 없는 id를 조회해 404만 재게 된다).
export const SEED = {
    userCount: Number(__ENV.SEED_USER_COUNT || 5000),
    mapCount: Number(__ENV.SEED_MAP_COUNT || 500),
};

/**
 * 테스트 전용 JWT 시크릿을 읽는다. 없거나 짧으면 즉시 중단한다.
 *
 * 기본값을 두지 않는 이유: 기본값이 있으면 시크릿을 안 넘긴 채로 실행돼도 그럭저럭 돌아가버려서,
 * 나중에 그 값이 코드에 박힌 진짜 시크릿으로 바뀌는 사고가 난다. 없으면 없다고 터지는 편이 안전하다.
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
    // k6가 목표 부하를 못 낸 회차는 결과가 무효다. 0이 아니면 폐기하고 재실행한다.
    dropped_iterations: ['count<1'],
};

/**
 * MODE 환경변수에 따라 부하 모양을 만든다.
 *
 * 전부 arrival-rate(open model)를 쓴다. VU 기반(closed model)은 서버가 느려지면 부하도 같이 줄어들어,
 * 리팩터링 전후를 비교할 때 "개선했는데 처리량은 그대로"인 것처럼 보인다.
 */
export function scenarioOptions(scenarioName) {
    const mode = __ENV.MODE || 'smoke';
    return {
        thresholds: DEFAULT_THRESHOLDS,
        scenarios: { [scenarioName]: executorFor(mode) },
        // id가 URL에 들어가면 지표가 요청마다 쪼개진다. 시나리오에서 tags.name 으로 묶는다.
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

/** 시드 범위 안에서 임의의 id를 고른다. id는 1부터 시작한다고 가정한다. */
export function randomId(max) {
    return Math.floor(Math.random() * max) + 1;
}
