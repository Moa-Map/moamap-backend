package com.moamap.map.exception;

import com.moamap.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * map-service 도메인 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum MapErrorCode implements ErrorCode {

    MAP_NOT_FOUND("MAP_001", "지도를 찾을 수 없습니다.", 404),
    NOT_MAP_MEMBER("MAP_002", "해당 지도의 멤버가 아닙니다.", 403),
    NO_MANAGE_PERMISSION("MAP_003", "지도를 관리할 권한이 없습니다.", 403),
    NOT_MAP_OWNER("MAP_004", "지도 소유자만 수행할 수 있습니다.", 403),
    ALREADY_JOINED("MAP_005", "이미 참여한 지도입니다.", 409),
    NOT_COMMUNITY_MAP("MAP_006", "공개 지도가 아니어서 바로 참여할 수 없습니다.", 400),
    INVALID_INVITE_CODE("MAP_007", "유효하지 않은 초대 코드입니다.", 404),
    OWNER_CANNOT_LEAVE("MAP_008", "소유자는 지도를 나갈 수 없습니다. 지도를 삭제하거나 소유권을 넘기세요.", 400),
    INVITE_CODE_GENERATION_FAILED("MAP_009", "초대 코드 생성에 실패했습니다. 다시 시도해 주세요.", 500),
    INVALID_FILE_TYPE("MAP_010", "허용되지 않은 파일 형식입니다.", 400),
    FILE_SIZE_EXCEEDED("MAP_011", "파일 크기가 허용치를 초과했습니다.", 400),
    STORAGE_NOT_CONFIGURED("MAP_012", "이미지 업로드를 사용할 수 없습니다.", 503),
    TARGET_NOT_MAP_MEMBER("MAP_013", "대상 사용자가 해당 지도의 멤버가 아닙니다.", 404),
    CANNOT_CHANGE_OWNER_ROLE("MAP_014", "소유자의 역할은 변경할 수 없습니다.", 400),
    INVALID_ROLE_ASSIGNMENT("MAP_015", "부여할 수 없는 역할입니다. ADMIN 또는 MEMBER만 지정할 수 있습니다.", 400),
    CANNOT_DELETE_PERSONAL_MAP("MAP_016", "나만의 지도는 삭제할 수 없습니다.", 400);

    private final String code;
    private final String message;
    private final int status;
}
