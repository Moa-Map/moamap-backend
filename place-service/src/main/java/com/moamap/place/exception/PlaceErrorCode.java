package com.moamap.place.exception;

import com.moamap.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * place-service 도메인 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum PlaceErrorCode implements ErrorCode {

    PLACE_NOT_FOUND("PLACE_001", "장소를 찾을 수 없습니다.", 404),
    NOT_MAP_MEMBER("PLACE_002", "해당 지도의 멤버가 아닙니다.", 403),
    OFFICIAL_MAP_NOT_REGISTRABLE("PLACE_003", "공식 지도에는 장소를 추가할 수 없습니다.", 403),
    NOT_PLACE_OWNER("PLACE_004", "해당 장소에 대한 권한이 없습니다.", 403),
    NOT_REVIEWER("PLACE_005", "방장/관리자만 접근할 수 있습니다.", 403),
    ALREADY_PROCESSED("PLACE_006", "이미 처리된 장소입니다.", 400),
    MAP_NOT_FOUND("PLACE_007", "지도를 찾을 수 없습니다.", 404),
    MAP_SERVICE_UNAVAILABLE("PLACE_008", "지도 서비스와 통신할 수 없습니다. 잠시 후 다시 시도해 주세요.", 503);

    private final String code;
    private final String message;
    private final int status;
}
