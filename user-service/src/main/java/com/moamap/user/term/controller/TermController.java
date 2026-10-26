package com.moamap.user.term.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.term.dto.TermDetailResponse;
import com.moamap.user.term.dto.TermSummaryResponse;
import com.moamap.user.term.service.TermService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Term", description = "약관 조회 API (로그인 없이 조회 가능)")
@RestController
@RequestMapping("/api/v1/terms")
@RequiredArgsConstructor
public class TermController {

    private final TermService termService;

    @Operation(summary = "약관 목록",
            description = "지금 보여줄 약관. 화면 순서대로 온다. 본문은 없다. "
                    + "REQUIRED는 체크박스로, VIEW_ONLY는 \"보기\" 링크로 그린다. 앱에 하드코딩하지 말고 이 결과대로 그린다.")
    @GetMapping
    public ApiResponse<List<TermSummaryResponse>> getTerms() {
        return ApiResponse.success(termService.getActiveTerms());
    }

    @Operation(summary = "약관 전문",
            description = "본문은 Markdown이다. HTML로 실행하지 말고 Markdown 렌더러로 그린다. "
                    + "지난 버전도 조회된다(active=false).")
    @GetMapping("/{termId}")
    public ApiResponse<TermDetailResponse> getTerm(@PathVariable Long termId) {
        return ApiResponse.success(termService.getTerm(termId));
    }
}
