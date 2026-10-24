package com.moamap.user.report.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.report.dto.ReportRequest;
import com.moamap.user.report.dto.ReportResponse;
import com.moamap.user.report.service.UserReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Report", description = "신고 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserReportController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final UserReportService userReportService;

    @Operation(summary = "사용자 신고",
            description = "프로필(닉네임·사진·소개)이 부적절한 사용자를 신고한다. 운영자가 받아 24시간 안에 검토한다. "
                    + "자기 자신은 신고할 수 없고(400, USER_024), 같은 사용자를 두 번 신고하면 409(USER_025). "
                    + "탈퇴했거나 없는 사용자는 404.")
    @PostMapping("/{userId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportResponse> report(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long reporterId,
            @PathVariable Long userId,
            @Valid @RequestBody ReportRequest request) {
        return ApiResponse.success(userReportService.report(reporterId, userId, request));
    }
}
