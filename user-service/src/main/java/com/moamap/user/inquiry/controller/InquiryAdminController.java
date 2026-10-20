package com.moamap.user.inquiry.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.inquiry.dto.InquiryDetailResponse;
import com.moamap.user.inquiry.dto.InquiryStatusUpdateRequest;
import com.moamap.user.inquiry.dto.InquirySummaryResponse;
import com.moamap.user.inquiry.entity.InquiryStatus;
import com.moamap.user.inquiry.entity.InquiryType;
import com.moamap.user.inquiry.service.InquiryAdminService;
import com.moamap.user.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 전용. 권한은 서비스가 회원 역할(Role.ADMIN)로 확인한다.
 * 응답에 이메일·문의 내용 같은 개인정보가 들어 있으니, 관리자 화면에서도 HTML이 아닌 텍스트로 표시해야 한다.
 */
@Tag(name = "Inquiry Admin", description = "문의 관리 API (관리자 전용)")
@RestController
@RequestMapping("/api/v1/admin/inquiries")
@RequiredArgsConstructor
public class InquiryAdminController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final InquiryAdminService inquiryAdminService;

    @Operation(summary = "문의 목록", description = "최신 접수순, 기본 20개·최대 50개. 상태·유형으로 거를 수 있다. 이메일과 전체 내용은 상세에서만 준다.")
    @GetMapping
    public ApiResponse<PageResponse<InquirySummaryResponse>> getInquiries(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long adminId,
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(required = false) InquiryType type,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(PageResponse.from(inquiryAdminService.getInquiries(adminId, status, type, pageable)));
    }

    @Operation(summary = "문의 상세", description = "답변 이메일과 문의 내용 전체를 준다.")
    @GetMapping("/{inquiryId}")
    public ApiResponse<InquiryDetailResponse> getInquiry(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long adminId,
            @PathVariable Long inquiryId) {
        return ApiResponse.success(inquiryAdminService.getInquiry(adminId, inquiryId));
    }

    @Operation(summary = "처리 상태 변경", description = "RECEIVED → IN_PROGRESS → ANSWERED. 답변 완료로 바꾸면 답변 시각을 기록한다.")
    @PatchMapping("/{inquiryId}/status")
    public ApiResponse<InquiryDetailResponse> changeStatus(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long adminId,
            @PathVariable Long inquiryId,
            @Valid @RequestBody InquiryStatusUpdateRequest request) {
        return ApiResponse.success(inquiryAdminService.changeStatus(adminId, inquiryId, request.status()));
    }
}
