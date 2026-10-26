package com.moamap.user.term.dto;

import com.moamap.user.term.entity.Term;
import com.moamap.user.term.entity.TermKind;
import io.swagger.v3.oas.annotations.media.Schema;

public record TermDetailResponse(
        Long termId,
        String code,
        String title,
        String version,
        TermKind kind,
        @Schema(description = "지금 보여주는 버전인지. 지난 버전(동의했던 문구 확인용)이면 false.")
        boolean active,
        @Schema(description = "본문(Markdown). HTML로 실행하지 말고 Markdown 렌더러로 그린다.",
                example = "# 서비스 이용약관\n\n## 제1조 (목적)\n...")
        String content
) {

    public static TermDetailResponse of(Term term, String content) {
        return new TermDetailResponse(term.getId(), term.getCode(), term.getTitle(), term.getVersion(),
                term.getKind(), term.isActive(), content);
    }
}
