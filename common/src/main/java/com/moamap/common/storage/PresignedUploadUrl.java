package com.moamap.common.storage;

/**
 * presigned URL 발급 결과 값 객체. 도메인 중립 — place/review/map 어떤 사진 발급에도 재사용된다.
 */
public record PresignedUploadUrl(
    String uploadUrl,
    String objectKey,
    String fileUrl,
    long expiresInSeconds
) {
}
