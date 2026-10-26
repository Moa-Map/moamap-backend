package com.moamap.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "로그 탭 게시물 댓글 작성 요청")
public record MapPostCommentCreateRequest(
    @Schema(description = "댓글 내용", example = "저도 가봤어요!")
    @NotBlank(message = "댓글 내용은 필수입니다.")
    @Size(max = 500, message = "댓글은 500자를 넘을 수 없습니다.")
    String content
) {
}
