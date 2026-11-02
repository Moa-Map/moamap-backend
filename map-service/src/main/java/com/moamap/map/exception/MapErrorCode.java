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
    // 방장은 나갈 때 다음 사람에게 자동으로 넘긴다. 넘길 사람이 없으면 지도가 방장 없이 남으므로 막는다.
    SOLE_OWNER_CANNOT_LEAVE("MAP_008", "혼자 남은 지도는 나갈 수 없습니다. 지도를 삭제해 주세요.", 400),
    INVITE_CODE_GENERATION_FAILED("MAP_009", "초대 코드 생성에 실패했습니다. 다시 시도해 주세요.", 500),
    INVALID_FILE_TYPE("MAP_010", "허용되지 않은 파일 형식입니다.", 400),
    FILE_SIZE_EXCEEDED("MAP_011", "파일 크기가 허용치를 초과했습니다.", 400),
    STORAGE_NOT_CONFIGURED("MAP_012", "이미지 업로드를 사용할 수 없습니다.", 503),
    TARGET_NOT_MAP_MEMBER("MAP_013", "대상 사용자가 해당 지도의 멤버가 아닙니다.", 404),
    CANNOT_CHANGE_OWNER_ROLE("MAP_014", "소유자의 역할은 변경할 수 없습니다.", 400),
    INVALID_ROLE_ASSIGNMENT("MAP_015", "부여할 수 없는 역할입니다. ADMIN 또는 MEMBER만 지정할 수 있습니다.", 400),
    CANNOT_DELETE_PERSONAL_MAP("MAP_016", "나만의 지도는 삭제할 수 없습니다.", 400),
    MAP_POST_NOT_SUPPORTED("MAP_017", "이 지도에는 로그 탭이 없습니다.", 404),
    MAP_POST_NOT_FOUND("MAP_018", "게시물을 찾을 수 없습니다.", 404),
    MAP_POST_COMMENT_NOT_FOUND("MAP_019", "댓글을 찾을 수 없습니다.", 404),
    DUPLICATE_PLACE_TAG("MAP_020", "같은 장소를 두 번 태그할 수 없습니다.", 400),
    RESTROOM_NOT_FOUND("MAP_021", "화장실 정보를 찾을 수 없습니다.", 404),
    // 없는 지도인지, 남의 지도인지 구분해서 알려주지 않는다. 메시지 차이로 멤버십을 캐낼 수 있기 때문이다.
    INVALID_MAP_ORDER("MAP_022", "순서를 바꾸려면 해당 탭에서 참여 중인 지도를 중복 없이 모두 보내야 합니다.", 400),
    CANNOT_LEAVE_PERSONAL_MAP("MAP_023", "나만의 지도는 나갈 수 없습니다.", 400),
    OFFICIAL_MAP_NOT_REPORTABLE("MAP_024", "공식 지도는 신고할 수 없습니다. 문의하기로 알려 주세요.", 400),
    CANNOT_REPORT_OWN_CONTENT("MAP_025", "내가 만든 지도나 쓴 글은 신고할 수 없습니다.", 400),
    ALREADY_REPORTED("MAP_026", "이미 신고했습니다.", 409),
    OWNER_HANDOVER_REQUIRED("MAP_027", "나가기 전에 방장을 위임해 주세요.", 400),
    NOT_OWNER_CANDIDATE("MAP_028", "새 방장으로 지정할 수 없는 사용자입니다. 후보 목록을 다시 확인해 주세요.", 400);

    private final String code;
    private final String message;
    private final int status;
}
