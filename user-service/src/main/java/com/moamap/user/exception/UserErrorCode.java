package com.moamap.user.exception;

import com.moamap.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * user-service 도메인 에러 코드. 앱은 message 문자열이 아니라 code로 분기한다.
 */
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    INVALID_OAUTH_TOKEN("USER_001", "카카오 인증에 실패했습니다.", 401),
    REFRESH_TOKEN_EXPIRED("USER_002", "리프레시 토큰이 만료되었습니다. 다시 로그인해 주세요.", 401),
    USER_NOT_FOUND("USER_003", "사용자를 찾을 수 없습니다.", 404),
    TOO_MANY_USER_IDS("USER_004", "한 번에 조회할 수 있는 사용자는 100명입니다.", 400);

    private final String code;
    private final String message;
    private final int status;
}
