// 부하 테스트용 JWT 발급.
//
// 게이트웨이(JwtAuthenticationFilter)는 Bearer 토큰을 검증한 뒤 subject 를 X-User-Id 헤더로 넣어준다.
// 클라이언트가 보낸 X-User-Id 는 무조건 버리므로, 헤더를 직접 위조해 게이트웨이를 우회할 수는 없다.
//
// 카카오 로그인(POST /api/v1/auth/kakao/login)을 수만 번 부르면 외부 레이트리밋과 계정 차단 위험이 있어,
// 게이트웨이와 같은 시크릿으로 토큰을 직접 서명해 쓴다.

import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

// 가장 긴 테스트(soak 30분) 중에 만료되지 않도록 넉넉히 잡는다.
const TOKEN_TTL_SEC = 2 * 60 * 60;

// 같은 유저의 토큰을 매 요청 다시 서명하면 부하 생성기 쪽 CPU를 괜히 쓴다. VU 안에서 재사용한다.
const tokenCache = new Map();

function base64UrlJson(value) {
    return encoding.b64encode(JSON.stringify(value), 'rawurl');
}

/** HS256으로 서명한 JWT를 만든다. JwtValidator 가 subject 를 userId 로 읽는다. */
export function signJwt(userId, secret) {
    const header = base64UrlJson({ alg: 'HS256', typ: 'JWT' });
    const issuedAt = Math.floor(Date.now() / 1000);
    const payload = base64UrlJson({
        sub: String(userId),
        iat: issuedAt,
        exp: issuedAt + TOKEN_TTL_SEC,
    });

    const signingInput = `${header}.${payload}`;
    const signature = crypto.hmac('sha256', secret, signingInput, 'base64rawurl');
    return `${signingInput}.${signature}`;
}

/** userId 에 해당하는 토큰을 돌려준다(없으면 만들어 캐시). */
export function tokenFor(userId, secret) {
    const cached = tokenCache.get(userId);
    if (cached) {
        return cached;
    }
    const token = signJwt(userId, secret);
    tokenCache.set(userId, token);
    return token;
}

/** 인증 헤더를 만든다. */
export function authHeaders(userId, secret) {
    return {
        headers: {
            Authorization: `Bearer ${tokenFor(userId, secret)}`,
            'Content-Type': 'application/json',
        },
    };
}
