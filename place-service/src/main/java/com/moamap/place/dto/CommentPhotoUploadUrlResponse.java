package com.moamap.place.dto;

public record CommentPhotoUploadUrlResponse(
    String uploadUrl,
    String objectKey,
    String fileUrl,
    long expiresInSeconds
) {
}
