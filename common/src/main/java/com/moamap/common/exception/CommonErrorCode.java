package com.moamap.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 모든 서비스가 공통으로 사용하는 에러 코드.
 * 서비스별 에러 코드는 각 서비스에서 ErrorCode를 구현하는 별도 enum으로 정의한다.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT_VALUE("COMMON_001", "요청 값이 올바르지 않습니다.", 400),
    METHOD_NOT_ALLOWED("COMMON_002", "허용되지 않은 HTTP 메서드입니다.", 405),
    ENTITY_NOT_FOUND("COMMON_003", "요청한 자원을 찾을 수 없습니다.", 404),
    ACCESS_DENIED("COMMON_004", "접근 권한이 없습니다.", 403),
    UNAUTHORIZED("COMMON_005", "인증이 필요합니다.", 401),
    INTERNAL_SERVER_ERROR("COMMON_006", "서버 내부 오류가 발생했습니다.", 500);

    private final String code;
    private final String message;
    private final int status;
}
