package com.moamap.user.inquiry.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.inquiry.dto.InquiryCreateRequest;
import com.moamap.user.inquiry.dto.InquiryCreateResponse;
import com.moamap.user.inquiry.service.InquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Inquiry", description = "문의하기 API")
@RestController
@RequestMapping("/api/v1/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    @Operation(summary = "문의 등록",
            description = "답변은 입력한 이메일로 관리자가 직접 보낸다. 최근 24시간 동안 10건까지 등록할 수 있고, 넘으면 429다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InquiryCreateResponse> create(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody InquiryCreateRequest request) {
        return ApiResponse.success(inquiryService.create(userId, request));
    }
}
