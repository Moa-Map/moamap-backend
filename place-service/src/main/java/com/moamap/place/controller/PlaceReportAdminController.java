package com.moamap.place.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.PlaceReportAdminResponse;
import com.moamap.place.entity.PlaceReportTargetType;
import com.moamap.place.service.PlaceReportAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 전용. 지도·로그 신고는 map-service, 사용자 신고는 user-service의 같은 이름 API에서 본다.
 */
@Tag(name = "Report Admin", description = "신고 확인 API (운영자 전용)")
@RestController
@RequestMapping("/api/v1/admin/reports/places")
@RequiredArgsConstructor
public class PlaceReportAdminController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final PlaceReportAdminService placeReportAdminService;

    @Operation(summary = "장소·장소 댓글 신고 목록",
        description = "최신순. 기본 20개, 최대 50개. targetType으로 장소(PLACE, 기본) 또는 장소 댓글(PLACE_COMMENT)을 고른다. "
            + "신고된 내용(이름·메모·댓글·사진)과 그 대상에 쌓인 신고 수를 함께 준다. 운영자가 아니면 403.")
    @GetMapping
    public ApiResponse<PageResponse<PlaceReportAdminResponse>> getReports(
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long adminId,
        @RequestParam(defaultValue = "PLACE") PlaceReportTargetType targetType,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(PageResponse.from(placeReportAdminService.getReports(adminId, targetType, pageable)));
    }
}
