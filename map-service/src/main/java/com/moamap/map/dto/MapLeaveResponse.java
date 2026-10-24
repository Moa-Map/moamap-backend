package com.moamap.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지도 나가기 결과. 방장이 나갔을 때만 새 방장이 담긴다.
 */
public record MapLeaveResponse(
    @Schema(description = "방장이 나가서 새로 방장이 된 사람. 관리자·멤버가 나갔으면 비어 있다.")
    NewOwner newOwner
) {

    public static MapLeaveResponse notOwner() {
        return new MapLeaveResponse(null);
    }

    public static MapLeaveResponse handedOverTo(Long userId, String nickname) {
        return new MapLeaveResponse(new NewOwner(userId, nickname));
    }

    public record NewOwner(
        Long userId,
        @Schema(description = "닉네임. 회원 정보를 가져오지 못하면 비어 있다.", example = "모아")
        String nickname
    ) {
    }
}
