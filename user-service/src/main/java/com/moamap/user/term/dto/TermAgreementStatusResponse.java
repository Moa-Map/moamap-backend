package com.moamap.user.term.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record TermAgreementStatusResponse(
        @Schema(description = "true면 동의 화면을 띄운다.")
        boolean needsAgreement,
        @Schema(description = "아직 동의하지 않은 필수 약관. 첫 가입이면 전체, 개정 후면 바뀐 약관만 들어 있다.")
        List<TermSummaryResponse> pendingTerms
) {

    public static TermAgreementStatusResponse of(List<TermSummaryResponse> pendingTerms) {
        return new TermAgreementStatusResponse(!pendingTerms.isEmpty(), pendingTerms);
    }
}
