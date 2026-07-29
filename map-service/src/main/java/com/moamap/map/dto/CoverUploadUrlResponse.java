package com.moamap.map.dto;

import com.moamap.common.storage.PresignedUploadUrl;

/**
 * 지도 커버 이미지 업로드 URL 발급 응답.
 *
 * uploadUrl로 파일을 PUT한 뒤, fileUrl을 지도 생성·수정 요청의 imageUrl에 담아 보낸다.
 */
public record CoverUploadUrlResponse(
    String uploadUrl,
    String objectKey,
    String fileUrl,
    long expiresInSeconds
) {

    public static CoverUploadUrlResponse from(PresignedUploadUrl presigned) {
        return new CoverUploadUrlResponse(
            presigned.uploadUrl(),
            presigned.objectKey(),
            presigned.fileUrl(),
            presigned.expiresInSeconds()
        );
    }
}
