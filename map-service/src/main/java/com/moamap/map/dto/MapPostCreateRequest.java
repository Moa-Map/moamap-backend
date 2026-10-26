package com.moamap.map.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "로그 탭 게시물 작성 요청")
public record MapPostCreateRequest(
    @Schema(description = "본문", example = "성수 카페 다녀왔어요")
    @NotBlank(message = "본문은 필수입니다.")
    @Size(max = 1000, message = "본문은 1000자를 넘을 수 없습니다.")
    String content,

    @Schema(description = "사진 URL 목록. presigned 업로드 후 받은 fileUrl을 순서대로 담는다.")
    @Size(max = 5, message = "사진은 5장까지 올릴 수 있습니다.")
    List<@NotBlank @Size(max = 1000) String> imageUrls,

    @Schema(description = "태그할 장소 목록")
    @Size(max = 10, message = "장소는 10개까지 태그할 수 있습니다.")
    List<@Valid MapPostPlaceTagRequest> placeTags
) {
}
