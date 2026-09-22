package com.moamap.place.dto;

import java.time.LocalDateTime;
import com.moamap.place.entity.CommentReportReason;
import com.moamap.place.entity.PlaceCommentReport;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 신고 접수 결과.
 *
 * 누적 신고 수는 담지 않는다. 신고자에게 "지금 몇 명이 신고했는지"를 알려주면 낙인 효과가 생기고
 * 몰아서 신고하는 행동을 부른다. 누적 수는 관리자 조회 응답에만 담는다.
 */
@Schema(description = "댓글 신고 접수 결과")
public record PlaceCommentReportResponse(
    @Schema(description = "신고한 댓글 ID", example = "12") Long commentId,
    @Schema(description = "접수된 신고 사유", example = "ABUSE") CommentReportReason reason,
    @Schema(description = "접수 시각") LocalDateTime reportedAt
) {

    public static PlaceCommentReportResponse from(PlaceCommentReport report) {
        return new PlaceCommentReportResponse(
            report.getPlaceCommentId(), report.getReason(), report.getCreatedAt());
    }
}
