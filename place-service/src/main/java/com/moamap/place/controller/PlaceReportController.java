package com.moamap.place.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.ReportRequest;
import com.moamap.place.dto.ReportResponse;
import com.moamap.place.dto.ReportedPlaceResponse;
import com.moamap.place.service.PlaceReportService;
import com.moamap.place.service.ReportedPlaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Place Report", description = "장소 신고 API")
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceReportController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final PlaceReportService placeReportService;
    private final ReportedPlaceService reportedPlaceService;

    @PostMapping("/{placeId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "장소 신고",
        description = """
            장소(사진·메모·태그)가 부적절하거나 정보가 틀렸을 때 신고한다. 운영자와 그 지도의 방장·관리자가 받는다.
            그 장소를 볼 수 있는 사람이면 신고할 수 있다 — 커뮤니티·공식 지도는 로그인한 누구나, 프라이빗 지도는 멤버만(403).
            지도에 올라온(승인된) 장소만 신고할 수 있다(그 외 404). 본인이 등록한 장소는 신고할 수 없다(400, PLACE_021).
            같은 장소를 두 번 신고하면 409(PLACE_022). 방장·관리자 알림은 그 장소의 첫 신고 때만 간다.
            """
    )
    public ApiResponse<ReportResponse> report(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
        @Valid @RequestBody ReportRequest request
    ) {
        return ApiResponse.success(placeReportService.report(placeId, userId, request));
    }

    @GetMapping("/reports")
    @Operation(
        summary = "신고된 장소 목록 (방장·관리자)",
        description = """
            내 지도에서 신고된 장소를 신고 많은 순으로 본다. **지도의 방장·관리자만** 볼 수 있다(403, PLACE_005).
            신고자는 보여주지 않는다. 유지하려면 그대로 두고, 삭제하려면 장소 삭제 API를 쓴다. 이미 삭제한 장소는 빠진다.
            """
    )
    public ApiResponse<PageResponse<ReportedPlaceResponse>> reportedPlaces(
        @Parameter(description = "조회할 지도 ID", required = true, example = "10") @RequestParam Long mapId,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(reportedPlaceService.findReportedPlaces(mapId, userId, pageable));
    }
}
