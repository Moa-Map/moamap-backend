package com.moamap.map.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.map.dto.ReportRequest;
import com.moamap.map.dto.ReportResponse;
import com.moamap.map.service.MapReportService;
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
@RequestMapping("/api/v1/maps/{mapId}")
@RequiredArgsConstructor
public class MapReportController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String COMMON_RULES = " 운영자가 받아 24시간 안에 검토한다. "
        + "커뮤니티 지도는 로그인한 누구나, 프라이빗 지도는 멤버만 신고할 수 있다(403, MAP_002). "
        + "내 것은 신고할 수 없고(400, MAP_025), 같은 대상을 두 번 신고하면 409(MAP_026).";

    private final MapReportService mapReportService;

    @Operation(summary = "지도 신고", description = "지도 이름·소개·커버 사진이 부적절할 때." + COMMON_RULES
        + " 공식 지도는 신고할 수 없다(400, MAP_024).")
    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportResponse> reportMap(
        @PathVariable Long mapId,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
        @Valid @RequestBody ReportRequest request
    ) {
        return ApiResponse.success(mapReportService.reportMap(mapId, userId, request));
    }

    @Operation(summary = "로그 신고", description = "로그(게시물) 내용·사진이 부적절할 때." + COMMON_RULES)
    @PostMapping("/posts/{postId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportResponse> reportPost(
        @PathVariable Long mapId,
        @PathVariable Long postId,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
        @Valid @RequestBody ReportRequest request
    ) {
        return ApiResponse.success(mapReportService.reportPost(mapId, postId, userId, request));
    }

    @Operation(summary = "로그 댓글 신고", description = "로그 댓글이 부적절할 때." + COMMON_RULES)
    @PostMapping("/posts/{postId}/comments/{commentId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportResponse> reportComment(
        @PathVariable Long mapId,
        @PathVariable Long postId,
        @PathVariable Long commentId,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
        @Valid @RequestBody ReportRequest request
    ) {
        return ApiResponse.success(mapReportService.reportComment(mapId, postId, commentId, userId, request));
    }
}
