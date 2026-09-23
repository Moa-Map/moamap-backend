package com.moamap.user.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AppleLoginRequest(
        @NotBlank String identityToken,
        @NotBlank String authorizationCode,
        @NotBlank String nonce,
        String fullName
) {
}
