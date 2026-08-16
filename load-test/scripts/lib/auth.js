// 부하 테스트용 인증 헤더.
//
// TARGET=gateway — 게이트웨이(JwtAuthenticationFilter)가 Bearer 토큰을 검증한 뒤 subject 를
//   X-User-Id 헤더로 넣어 뒷단에 넘긴다. 클라이언트가 보낸 X-User-Id 는 무조건 버리므로
//   헤더를 위조해 게이트웨이를 우회할 수는 없다. 그래서 토큰을 직접 서명해 보낸다.
//   카카오 로그인(POST /api/v1/auth/kakao/login)을 수만 번 부르면 외부 레이트리밋과
//   계정 차단 위험이 있어 로그인 흐름은 쓰지 않는다.
//
// TARGET=direct — 게이트웨이를 건너뛰므로 뒷단 서비스가 기대하는 X-User-Id 를 직접 넣는다.
//   JWT 발급이 필요 없고, 프록시 구간이 빠져 측정 대상이 서비스 자체로 좁혀진다.

import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import { TARGET, requireSecret } from './config.js';

// 가장 긴 테스트(soak 30분) 중에 만료되지 않도록 넉넉히 잡는다.
const TOKEN_TTL_SEC = 2 * 60 * 60;

// 같은 유저의 토큰을 매 요청 다시 서명하면 부하 생성기 쪽 CPU를 괜히 쓴다. VU 안에서 재사용한다.
const tokenCache = new Map();

// direct 모드는 시크릿이 필요 없다. gateway 모드만 확인하되, 반복 중이 아니라 로드 시점에 터뜨린다
// — 매 반복마다 같은 예외를 뿜으면 로그만 더럽고 원인을 찾기 어렵다.
const SECRET = TARGET === 'gateway' ? requireSecret() : null;

function base64UrlJson(value) {
    return encoding.b64encode(JSON.stringify(value), 'rawurl');
}

/** HS256으로 서명한 JWT를 만든다. JwtValidator 가 subject 를 userId 로 읽는다. */
export function signJwt(userId, jwtSecret) {
    const header = base64UrlJson({ alg: 'HS256', typ: 'JWT' });
    const issuedAt = Math.floor(Date.now() / 1000);
    const payload = base64UrlJson({
        sub: String(userId),
        iat: issuedAt,
        exp: issuedAt + TOKEN_TTL_SEC,
    });

    const signingInput = `${header}.${payload}`;
    const signature = crypto.hmac('sha256', jwtSecret, signingInput, 'base64rawurl');
    return `${signingInput}.${signature}`;
}

/** userId 에 해당하는 토큰을 돌려준다(없으면 만들어 캐시). */
function tokenFor(userId) {
    const cached = tokenCache.get(userId);
    if (cached) {
        return cached;
    }
    const token = signJwt(userId, SECRET);
    tokenCache.set(userId, token);
    return token;
}

/** 현재 TARGET 에 맞는 인증 헤더를 만든다. */
export function authHeaders(userId) {
    const headers =
        TARGET === 'gateway'
            ? { Authorization: `Bearer ${tokenFor(userId)}` }
            : { 'X-User-Id': String(userId) };
    return { headers: { ...headers, 'Content-Type': 'application/json' } };
}
