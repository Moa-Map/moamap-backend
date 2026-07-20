package com.moamap.user.user.dto;

import com.moamap.user.user.entity.Role;
import java.time.LocalDateTime;

public record MyPageResponse(
        Long id,
        String nickname,
        String email,
        String profileImageUrl,
        String provider,
        Role role,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        String introduction
) {
}