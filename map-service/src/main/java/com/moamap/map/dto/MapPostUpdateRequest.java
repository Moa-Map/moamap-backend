package com.moamap.map.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "로그 탭 게시물 수정 요청. null인 필드는 기존 값을 유지한다.")
public record MapPostUpdateRequest(
    @Schema(description = "본문", example = "성수 카페 다녀왔어요 (사진 추가)")
    @Size(min = 1, max = 1000, message = "본문은 1자 이상 1000자 이하여야 합니다.")
    String content,

    @Schema(description = "사진 URL 목록. 넘기면 전체 교체된다.")
    @Size(max = 5, message = "사진은 5장까지 올릴 수 있습니다.")
    List<@NotBlank @Size(max = 1000) String> imageUrls,

    @Schema(description = "태그할 장소 목록. 넘기면 전체 교체된다.")
    @Size(max = 10, message = "장소는 10개까지 태그할 수 있습니다.")
    List<@Valid MapPostPlaceTagRequest> placeTags
) {
}
