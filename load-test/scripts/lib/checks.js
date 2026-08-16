// 응답 검증 헬퍼.
//
// 상태 코드만 보면 안 된다. 시드가 안 들어갔거나 조회 파라미터가 틀리면 200에 빈 목록이 오는데,
// 그대로 두면 "빈 응답을 빠르게 반환하는 것"을 측정하게 된다. 실제 데이터가 실려 오는지까지 본다.

import { check } from 'k6';

/**
 * 공통 응답 규약(ApiResponse{success, data, error})에 맞는 정상 응답인지 확인한다.
 *
 * @param res       k6 응답
 * @param label     지표·로그에 쓸 이름
 * @param hasData   data 가 유효한지 판단하는 함수. 없으면 data 존재 여부만 본다.
 */
export function checkApiOk(res, label, hasData) {
    return check(res, {
        [`${label}: 200`]: (r) => r.status === 200,
        [`${label}: 응답에 데이터 있음`]: (r) => {
            if (r.status !== 200) {
                return false;
            }
            let body;
            try {
                body = r.json();
            } catch (e) {
                return false;
            }
            if (!body || body.success !== true) {
                return false;
            }
            return hasData ? hasData(body.data) : body.data !== null && body.data !== undefined;
        },
    });
}

/** 생성 계열 응답(201 또는 200) 확인. */
export function checkApiCreated(res, label) {
    return check(res, {
        [`${label}: 2xx`]: (r) => r.status === 200 || r.status === 201,
    });
}

/** 목록 응답이 비어 있지 않은지. Smoke 단계에서 시드 적재를 확인하는 용도. */
export function nonEmptyList(data) {
    if (Array.isArray(data)) {
        return data.length > 0;
    }
    // PageResponse 형태
    if (data && Array.isArray(data.content)) {
        return data.content.length > 0;
    }
    return false;
}
