package com.moamap.map.dto;

import com.moamap.common.storage.PresignedUploadUrl;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * uploadUrl로 파일을 PUT한 뒤, fileUrl을 게시물 작성 요청의 imageUrls에 담아 보낸다.
 */
@Schema(description = "로그 탭 게시물 사진 업로드 URL 발급 응답")
public record MapPostPhotoUploadUrlResponse(
    String uploadUrl,
    String objectKey,
    String fileUrl,
    long expiresInSeconds
) {
    public static MapPostPhotoUploadUrlResponse from(PresignedUploadUrl presigned) {
        return new MapPostPhotoUploadUrlResponse(presigned.uploadUrl(), presigned.objectKey(),
            presigned.fileUrl(), presigned.expiresInSeconds());
    }
}
