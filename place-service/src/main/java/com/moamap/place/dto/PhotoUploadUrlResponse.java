package com.moamap.place.dto;

public record PhotoUploadUrlResponse(
    String uploadUrl,
    String objectKey,
    String fileUrl,
    long expiresInSeconds
) {
}
