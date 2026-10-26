package com.moamap.user.user.dto;

import com.moamap.user.user.entity.Role;
import java.time.Instant;

public record MyPageResponse(
        Long id,
        String nickname,
        String email,
        String profileImageUrl,
        String provider,
        Role role,
        // UTC 오프셋(...Z) 포함 ISO-8601로 직렬화된다. 앱은 파싱 후 기기 로컬(KST)로 변환.
        Instant lastLoginAt,
        Instant createdAt,
        String introduction
) {
}