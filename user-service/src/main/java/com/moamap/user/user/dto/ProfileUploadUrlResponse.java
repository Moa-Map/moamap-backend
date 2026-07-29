package com.moamap.user.user.dto;

import com.moamap.common.storage.PresignedUploadUrl;

/**
 * 프로필 이미지 업로드 URL 발급 응답.
 *
 * uploadUrl로 파일을 PUT한 뒤, fileUrl을 마이페이지 수정 요청에 담아 보낸다.
 */
public record ProfileUploadUrlResponse(
    String uploadUrl,
    String objectKey,
    String fileUrl,
    long expiresInSeconds
) {

    public static ProfileUploadUrlResponse from(PresignedUploadUrl presigned) {
        return new ProfileUploadUrlResponse(
            presigned.uploadUrl(),
            presigned.objectKey(),
            presigned.fileUrl(),
            presigned.expiresInSeconds()
        );
    }
}
