package com.moamap.user.term.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.term.dto.TermAgreementRequest;
import com.moamap.user.term.dto.TermAgreementStatusResponse;
import com.moamap.user.term.service.TermAgreementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Term Agreement", description = "내 약관 동의 API")
@RestController
@RequestMapping("/api/v1/terms/agreements")
@RequiredArgsConstructor
public class TermAgreementController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final TermAgreementService termAgreementService;

    @Operation(summary = "내 동의 현황",
            description = "로그인 직후(앱 실행 시) 호출한다. needsAgreement가 true면 동의 화면을 띄우고 pendingTerms만 보여준다. "
                    + "첫 가입이면 필수 약관 전체, 약관 개정 후면 바뀐 약관만 들어 있다.")
    @GetMapping("/me")
    public ApiResponse<TermAgreementStatusResponse> getMyStatus(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long userId) {
        return ApiResponse.success(termAgreementService.getMyStatus(userId));
    }

    @Operation(summary = "약관 동의",
            description = "pendingTerms의 약관 ID를 전부 보낸다. 빠지면 400(USER_022). "
                    + "그사이 약관이 바뀌어 지난 버전을 보내면 409(USER_023) → 동의 현황을 다시 받아 화면을 새로 그린다. "
                    + "이미 동의한 약관은 다시 저장하지 않으므로 같은 요청을 다시 보내도 된다.")
    @PostMapping
    public ApiResponse<TermAgreementStatusResponse> agree(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long userId,
            @Valid @RequestBody TermAgreementRequest request) {
        return ApiResponse.success(termAgreementService.agree(userId, request));
    }
}
