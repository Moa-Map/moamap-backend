package com.moamap.place.dto;

import java.math.BigDecimal;
import java.util.List;
import com.moamap.place.entity.PlaceSourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 장소 일괄 등록 요청.
 *
 * Item은 PlaceCreateRequest에서 mapId를 뺀 모양이라, 지도 공유 링크 전용이 아니라
 * 범용 일괄 등록으로 쓸 수 있다.
 */
public record PlaceBulkCreateRequest(
    @NotNull Long mapId,
    @NotEmpty(message = "등록할 장소가 없습니다.")
    @Size(max = 100, message = "한 번에 최대 100개까지 등록할 수 있습니다.")
    List<@Valid Item> places
) {

    public record Item(
        @NotBlank String name,
        String address,
        String roadAddress,
        @NotNull BigDecimal lat,
        @NotNull BigDecimal lng,
        String category,
        @NotBlank String kakaoPlaceId,
        @NotNull PlaceSourceType sourceType,
        String sourceUrl,
        String description,
        List<@Size(max = 30) String> tags,
        @Size(max = 5) List<@Size(max = 1000) String> photoUrls
    ) {
    }
}
