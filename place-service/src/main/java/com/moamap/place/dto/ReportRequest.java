package com.moamap.place.dto;

import com.moamap.place.entity.ReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "장소 신고 요청")
public record ReportRequest(

    @Schema(description = "신고 사유. 폐업·위치가 틀림은 FALSE_INFO", example = "FALSE_INFO")
    @NotNull(message = "신고 사유는 필수입니다.")
    ReportReason reason,

    @Schema(description = "부가 설명. 사유가 OTHER면 필수.", example = "폐업한 가게예요.")
    @Size(max = 200, message = "부가 설명은 200자를 넘을 수 없습니다.")
    String detail
) {

    @Schema(hidden = true)
    @AssertTrue(message = "기타 사유는 내용을 적어야 합니다.")
    public boolean isDetailPresentWhenOther() {
        return reason != ReportReason.OTHER || (detail != null && !detail.isBlank());
    }
}
