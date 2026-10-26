package com.moamap.map.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MapUpdateRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description,
    @Size(max = 1000) String imageUrl,
    List<@Size(max = 30) String> tags,
    @Schema(description = "일반 멤버가 URL로 장소를 추가할 수 있는지. 보내지 않으면 지금 값을 유지한다", example = "true")
    Boolean allowUrlImport
) {
}
