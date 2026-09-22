package com.moamap.place.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.PlaceCommentReportRequest;
import com.moamap.place.dto.PlaceCommentReportResponse;
import com.moamap.place.dto.ReportedCommentResponse;
import com.moamap.place.service.PlaceCommentReportService;
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

/**
 * 댓글 신고. 접수는 장소 하위 경로, 관리자 조회는 지도 단위라 경로 모양이 달라 한 컨트롤러에 모았다.
 */
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
@Tag(name = "PlaceCommentReport", description = "장소 댓글 신고 API")
public class PlaceCommentReportController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final PlaceCommentReportService placeCommentReportService;

    @PostMapping("/{placeId}/comments/{commentId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "댓글 신고",
        description = """
            부적절한 댓글을 신고한다. 해당 지도의 멤버만 신고할 수 있고, 본인이 쓴 댓글은 신고할 수 없다(400).
            같은 댓글을 두 번 신고하면 409를 받는다.
            신고가 쌓여도 서버가 자동으로 숨기거나 지우지 않는다 — 지도 방장·관리자가 목록을 보고 판단한다.
            """
    )
    public ApiResponse<PlaceCommentReportResponse> report(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
        @Parameter(description = "댓글 ID", example = "12") @PathVariable Long commentId,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
        @Valid @RequestBody PlaceCommentReportRequest request
    ) {
        return ApiResponse.success(placeCommentReportService.report(placeId, commentId, userId, request));
    }

    @GetMapping("/comments/reported")
    @Operation(
        summary = "신고 누적 댓글 목록 조회",
        description = """
            지도에서 신고가 하나라도 쌓인 댓글을 많이 신고된 순으로 조회한다. **지도의 OWNER/ADMIN만** 볼 수 있다.
            누적 신고 수는 이 응답에만 담긴다 — 일반 댓글 목록에는 노출하지 않는다.
            """
    )
    public ApiResponse<PageResponse<ReportedCommentResponse>> getReported(
        @Parameter(description = "조회할 지도 ID", required = true, example = "10") @RequestParam Long mapId,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(placeCommentReportService.findReported(mapId, userId, pageable));
    }
}
