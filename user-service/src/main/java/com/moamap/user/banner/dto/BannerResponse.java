package com.moamap.user.banner.dto;

import com.moamap.user.banner.entity.Banner;

/**
 * 앱 메인 배너 한 장. 순서는 목록 순서로 전달한다.
 */
public record BannerResponse(Long bannerId, String imageUrl) {

    public static BannerResponse from(Banner banner) {
        return new BannerResponse(banner.getId(), banner.getImageUrl());
    }
}
