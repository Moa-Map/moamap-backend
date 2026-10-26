package com.moamap.map.user.dto;

/**
 * user-service의 프로필 응답을 받기 위한 형태. 서비스 간에는 코드가 아니라 응답 모양만 약속한다.
 * user-service의 UserProfileResponse와 필드명이 같아야 역직렬화된다.
 */
public record UserProfileResponse(
    Long id,
    String nickname,
    String profileImageUrl
) {
}
