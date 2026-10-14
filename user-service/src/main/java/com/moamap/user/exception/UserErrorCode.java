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
    TOO_MANY_USER_IDS("USER_004", "한 번에 조회할 수 있는 사용자는 100명입니다.", 400),
    INVALID_FILE_TYPE("USER_005", "허용되지 않은 파일 형식입니다.", 400),
    FILE_SIZE_EXCEEDED("USER_006", "파일 크기가 허용치를 초과했습니다.", 400),
    STORAGE_NOT_CONFIGURED("USER_007", "이미지 업로드를 사용할 수 없습니다.", 503),
    INVALID_APPLE_TOKEN("USER_008", "Apple 인증에 실패했습니다. 다시 로그인해 주세요.", 401),
    APPLE_AUTH_UNAVAILABLE("USER_009", "Apple 로그인을 일시적으로 사용할 수 없습니다.", 503),
    CONCURRENT_UPDATE("USER_010", "다른 요청과 동시에 처리되어 반영하지 못했습니다. 다시 시도해 주세요.", 409),
    // USER_011은 공지사항(#127)이 쓴다. 두 PR이 따로 머지돼도 번호가 겹치지 않게 비워 둔다.
    INQUIRY_NOT_FOUND("USER_012", "문의를 찾을 수 없습니다.", 404),
    TOO_MANY_INQUIRIES("USER_013", "문의는 하루 10건까지 보낼 수 있습니다. 잠시 후 다시 시도해 주세요.", 429);

    private final String code;
    private final String message;
    private final int status;
}
