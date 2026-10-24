package com.moamap.place.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.place.dto.PlaceCommentReportRequest;
import com.moamap.place.dto.PlaceCommentReportResponse;
import com.moamap.place.service.PlaceCommentReportService;
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
            부적절한 댓글을 신고한다. 운영자가 받아 24시간 안에 검토한다(방장·관리자에게는 가지 않는다).
            그 댓글을 볼 수 있는 사람이면 신고할 수 있다 — 커뮤니티·공식 지도는 로그인한 누구나, 프라이빗 지도는 멤버만(403).
            본인이 쓴 댓글은 신고할 수 없다(400). 같은 댓글을 두 번 신고하면 409를 받는다.
            신고가 쌓여도 서버가 자동으로 숨기거나 지우지 않는다.
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
}
