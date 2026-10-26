package com.moamap.map.dto;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MapUpdateRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description,
    @Size(max = 1000) String imageUrl,
    List<@Size(max = 30) String> tags
) {
}
