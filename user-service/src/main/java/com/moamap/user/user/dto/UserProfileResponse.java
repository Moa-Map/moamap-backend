package com.moamap.user.user.dto;

// place-service com.moamap.place.user.dto.UserProfileResponse가 이 응답을 역직렬화한다 — 필드명 변경 금지.
public record UserProfileResponse(
    Long id,
    String nickname,
    String profileImageUrl
) {
}
