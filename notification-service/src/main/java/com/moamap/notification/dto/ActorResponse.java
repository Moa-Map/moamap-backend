package com.moamap.notification.dto;

import com.moamap.notification.user.UserProfiles;
import com.moamap.notification.user.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 알림을 일으킨 사람.
 *
 * - 정상 회원: ID·닉네임·프로필 이미지
 * - 탈퇴한 회원: ID 없이 "탈퇴한 사용자"
 * - 회원 정보를 잠시 가져오지 못한 경우: ID만 있고 닉네임은 비어 있다(앱은 기본 표시를 쓴다)
 */
public record ActorResponse(
        @Schema(description = "회원 ID. 탈퇴한 회원이면 비어 있다.")
        Long userId,
        @Schema(description = "닉네임. 회원 정보를 가져오지 못하면 비어 있다.", example = "모아")
        String nickname,
        String profileImageUrl
) {

    static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

    /** 행동한 사람이 없는 알림이면 null이다. */
    static ActorResponse resolve(Long actorId, UserProfiles profiles) {
        if (actorId == null) {
            return null;
        }
        if (!profiles.isAvailable()) {
            return new ActorResponse(actorId, null, null);
        }
        return profiles.find(actorId)
                .map(ActorResponse::from)
                .orElseGet(() -> new ActorResponse(null, WITHDRAWN_NICKNAME, null));
    }

    private static ActorResponse from(UserProfileResponse profile) {
        return new ActorResponse(profile.id(), profile.nickname(), profile.profileImageUrl());
    }
}
