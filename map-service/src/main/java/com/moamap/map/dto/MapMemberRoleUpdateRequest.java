package com.moamap.map.dto;

import com.moamap.map.entity.MapRole;
import jakarta.validation.constraints.NotNull;

/**
 * 멤버 역할 변경 요청. ADMIN/MEMBER 외의 값 검증은 서비스에서 한다.
 */
public record MapMemberRoleUpdateRequest(
    @NotNull MapRole role
) {
}
