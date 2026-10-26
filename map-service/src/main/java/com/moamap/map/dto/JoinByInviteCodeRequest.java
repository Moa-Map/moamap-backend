package com.moamap.map.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinByInviteCodeRequest(
    @NotBlank String inviteCode
) {
}
