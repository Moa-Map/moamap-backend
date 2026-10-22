package com.moamap.user.term.dto;

import com.moamap.user.term.entity.Term;
import com.moamap.user.term.entity.TermKind;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 약관 목록의 한 줄. 본문은 담지 않는다(전문 조회로 따로 받는다).
 */
public record TermSummaryResponse(
        @Schema(description = "약관 ID. 동의할 때 이 값을 보낸다.", example = "4")
        Long termId,
        @Schema(description = "약관 종류 식별자. 버전이 달라도 같은 약관이면 같다.", example = "SERVICE")
        String code,
        @Schema(example = "서비스 이용약관")
        String title,
        @Schema(example = "v2")
        String version,
        @Schema(description = "REQUIRED: 체크박스(동의 필요) / VIEW_ONLY: 보기만")
        TermKind kind
) {

    public static TermSummaryResponse from(Term term) {
        return new TermSummaryResponse(term.getId(), term.getCode(), term.getTitle(), term.getVersion(), term.getKind());
    }
}
