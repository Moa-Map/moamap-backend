package com.moamap.map.dto;

import com.moamap.map.entity.PlaceTag;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "게시물에 태그된 장소")
public record MapPostPlaceTagResponse(
    @Schema(description = "장소 ID", example = "5") Long placeId,
    @Schema(description = "태그 시점의 장소 이름", example = "블루보틀 성수점") String name
) {
    public static MapPostPlaceTagResponse from(PlaceTag tag) {
        return new MapPostPlaceTagResponse(tag.placeId(), tag.placeName());
    }
}
