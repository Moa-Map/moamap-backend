package com.moamap.user.term.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TermAgreementRequest(

        @Schema(description = "동의할 약관 ID. 동의 현황의 pendingTerms를 전부 보내야 한다.", example = "[4]")
        @NotEmpty(message = "동의할 약관을 보내야 합니다.")
        @Size(max = TermAgreementRequest.MAX_TERMS, message = "한 번에 20개까지 보낼 수 있습니다.")
        List<@NotNull @Positive Long> termIds
) {

    public static final int MAX_TERMS = 20;
}
