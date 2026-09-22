package com.moamap.place.dto;

import com.moamap.place.entity.CommentReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "댓글 신고 요청")
public record PlaceCommentReportRequest(

    @Schema(description = "신고 사유", example = "ABUSE",
        allowableValues = {"SPAM", "ABUSE", "SEXUAL", "FALSE_INFO", "OTHER"})
    @NotNull(message = "신고 사유는 필수입니다.")
    CommentReportReason reason,

    @Schema(description = "부가 설명(선택). 사유가 OTHER일 때 특히 유용하다.", example = "다른 장소 광고를 붙여넣었습니다.")
    @Size(max = 200, message = "부가 설명은 200자를 넘을 수 없습니다.")
    String detail
) {
}
