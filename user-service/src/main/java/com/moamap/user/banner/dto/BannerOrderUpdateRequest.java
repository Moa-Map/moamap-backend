package com.moamap.user.banner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 배너 순서 변경. 등록된 배너 전체를 원하는 순서대로 담는다(지도 순서 변경과 같은 방식).
 */
public record BannerOrderUpdateRequest(

        @Schema(description = "원하는 순서대로 나열한 배너 ID. 등록된 배너 전체를 빠짐없이 담는다.")
        @NotEmpty(message = "배너 목록은 비어 있을 수 없습니다.")
        @Size(max = 10, message = "배너는 최대 10개입니다.")
        List<@NotNull(message = "배너 ID는 비어 있을 수 없습니다.") Long> bannerIds
) {
}
