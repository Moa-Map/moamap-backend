package com.moamap.place.dto;

public record ReviewPhotoUploadUrlResponse(
    String uploadUrl,
    String objectKey,
    String fileUrl,
    long expiresInSeconds
) {
}
