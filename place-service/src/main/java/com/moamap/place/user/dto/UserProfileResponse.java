package com.moamap.place.user.dto;

// 원본: user-service com.moamap.user.user.dto.UserProfileResponse — 필드명 변경 시 이 파일도 함께 수정.
public record UserProfileResponse(
    Long id,
    String nickname,
    String profileImageUrl
) {
}
