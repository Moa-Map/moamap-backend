package com.moamap.user.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 로그인·토큰 재발급 응답.
 *
 * @param userId                로그인한 회원 식별자.
 * @param expiresIn             액세스 토큰 만료까지 남은 시간(초)
 * @param refreshTokenExpiresIn 리프레시 토큰 만료까지 남은 시간(초). 만료 시 앱은 재로그인 유도.
 * @param isNewUser             이번 로그인으로 신규 가입됐는지 여부. 로그인 직후 온보딩 분기용. (재발급은 항상 false)
 */
public record TokenResponse(
        Long userId,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshTokenExpiresIn,
        // record 접근자 isNewUser()는 Jackson이 "newUser"로 직렬화하므로 필드명을 명시 고정한다.
        @JsonProperty("isNewUser") boolean isNewUser
) {
}
