package com.moamap.user.banner.dto;

import com.moamap.user.banner.entity.Banner;
import java.time.Instant;

public record AdminBannerResponse(
        Long bannerId,
        String imageUrl,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {

    public static AdminBannerResponse from(Banner banner) {
        return new AdminBannerResponse(banner.getId(), banner.getImageUrl(), banner.getDisplayOrder(),
                banner.getCreatedAt(), banner.getUpdatedAt());
    }
}
