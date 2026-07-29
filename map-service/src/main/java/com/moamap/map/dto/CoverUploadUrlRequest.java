package com.moamap.map.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 지도 커버 이미지 업로드 URL 발급 요청.
 *
 * mapId를 받지 않는다. 사용자는 지도를 만들기 전에 커버를 고르므로 이 시점에는 지도가 존재하지 않는다.
 */
public record CoverUploadUrlRequest(
    @NotBlank String contentType,
    @NotNull @Positive Long fileSize
) {
}
