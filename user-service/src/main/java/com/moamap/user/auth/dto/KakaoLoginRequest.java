package com.moamap.user.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(@NotBlank String kakaoAccessToken) {
}
