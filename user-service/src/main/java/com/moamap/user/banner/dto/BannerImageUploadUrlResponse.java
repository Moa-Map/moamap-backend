package com.moamap.user.banner.dto;

import com.moamap.common.storage.PresignedUploadUrl;

/**
 * uploadUrl로 파일을 PUT한 뒤, fileUrl을 배너 등록·이미지 교체 요청에 담아 보낸다.
 */
public record BannerImageUploadUrlResponse(String uploadUrl, String fileUrl, long expiresInSeconds) {

    public static BannerImageUploadUrlResponse from(PresignedUploadUrl presigned) {
        return new BannerImageUploadUrlResponse(presigned.uploadUrl(), presigned.fileUrl(), presigned.expiresInSeconds());
    }
}
