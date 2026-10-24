package com.moamap.user.report.dto;

import com.moamap.user.report.entity.ReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "신고 요청")
public record ReportRequest(

        @Schema(description = "신고 사유. BLOCKED는 서버 전용이라 보낼 수 없다.", example = "ABUSE")
        @NotNull(message = "신고 사유는 필수입니다.")
        ReportReason reason,

        @Schema(description = "부가 설명. 사유가 OTHER면 필수.", example = "다른 사람의 사진을 프로필로 쓰고 있어요.")
        @Size(max = 200, message = "부가 설명은 200자를 넘을 수 없습니다.")
        String detail
) {

    @Schema(hidden = true)
    @AssertTrue(message = "고를 수 없는 신고 사유입니다.")
    public boolean isSelectableReason() {
        return reason != ReportReason.BLOCKED;
    }

    @Schema(hidden = true)
    @AssertTrue(message = "기타 사유는 내용을 적어야 합니다.")
    public boolean isDetailPresentWhenOther() {
        return reason != ReportReason.OTHER || (detail != null && !detail.isBlank());
    }
}
