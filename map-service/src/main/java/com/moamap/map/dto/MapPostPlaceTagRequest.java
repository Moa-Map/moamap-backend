package com.moamap.map.dto;

import com.moamap.map.entity.PlaceTag;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "게시물에 태그할 장소. 태그 피커에서 고른 id와 이름을 그대로 보낸다.")
public record MapPostPlaceTagRequest(
    @Schema(description = "장소 ID", example = "5")
    @NotNull(message = "장소 ID는 필수입니다.")
    Long placeId,

    @Schema(description = "장소 이름", example = "블루보틀 성수점")
    @NotBlank(message = "장소 이름은 필수입니다.")
    @Size(max = 255, message = "장소 이름은 255자를 넘을 수 없습니다.")
    String name
) {
    public PlaceTag toEntity() {
        return new PlaceTag(placeId, name);
    }
}
