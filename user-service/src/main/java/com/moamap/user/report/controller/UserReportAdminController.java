package com.moamap.user.report.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.report.dto.UserReportAdminResponse;
import com.moamap.user.report.service.UserReportAdminService;
import com.moamap.user.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 전용. 장소·댓글 신고는 place-service, 지도·로그 신고는 map-service의 같은 이름 API에서 본다.
 */
@Tag(name = "Report Admin", description = "신고 확인 API (운영자 전용)")
@RestController
@RequestMapping("/api/v1/admin/reports/users")
@RequiredArgsConstructor
public class UserReportAdminController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final UserReportAdminService userReportAdminService;

    @Operation(summary = "사용자 신고 목록",
            description = "최신순. 기본 20개, 최대 50개. 신고된 프로필 내용과 그 사용자에게 쌓인 신고 수를 함께 준다.")
    @GetMapping
    public ApiResponse<PageResponse<UserReportAdminResponse>> getReports(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long adminId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(PageResponse.from(userReportAdminService.getReports(adminId, pageable)));
    }
}
