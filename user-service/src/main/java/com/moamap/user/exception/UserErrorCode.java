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
    NOTICE_NOT_FOUND("USER_011", "공지사항을 찾을 수 없습니다.", 404),
    INQUIRY_NOT_FOUND("USER_012", "문의를 찾을 수 없습니다.", 404),
    TOO_MANY_INQUIRIES("USER_013", "문의는 하루 10건까지 보낼 수 있습니다. 잠시 후 다시 시도해 주세요.", 429),
    BANNER_NOT_FOUND("USER_014", "배너를 찾을 수 없습니다.", 404),
    BANNER_LIMIT_EXCEEDED("USER_015", "배너는 최대 10개까지 등록할 수 있습니다.", 409),
    INVALID_BANNER_ORDER("USER_016", "순서를 바꾸려면 등록된 배너를 중복 없이 모두 보내야 합니다.", 400),
    INVALID_BANNER_IMAGE("USER_017", "배너 이미지는 배너 업로드 주소로 올린 파일만 쓸 수 있습니다.", 400),
    INVALID_GOOGLE_TOKEN("USER_018", "Google 인증에 실패했습니다. 다시 로그인해 주세요.", 401),
    GOOGLE_AUTH_UNAVAILABLE("USER_019", "Google 로그인을 일시적으로 사용할 수 없습니다.", 503),
    TERM_NOT_FOUND("USER_020", "약관을 찾을 수 없습니다.", 404),
    INVALID_TERM_AGREEMENT("USER_021", "동의할 수 없는 약관이 포함되어 있습니다.", 400),
    REQUIRED_TERMS_NOT_AGREED("USER_022", "필수 약관에 모두 동의해야 합니다.", 400),
    // 동의 화면을 띄운 사이에 약관이 개정된 경우. 앱은 동의 현황을 다시 받아 화면을 새로 그린다.
    TERM_OUTDATED("USER_023", "약관이 갱신되었습니다. 바뀐 약관을 확인해 주세요.", 409);

    private final String code;
    private final String message;
    private final int status;
}
