package com.moamap.place.dto;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 신고가 쌓인 댓글. 지도 방장·관리자가 지울지 판단하는 데 필요한 것만 담는다.
 *
 * JPQL 생성자 표현식으로 채우므로 필드 순서와 타입이 쿼리와 정확히 맞아야 한다
 * (PlaceCommentRepository.findReportedByMapId 참고).
 */
@Schema(description = "신고 누적 댓글 (지도 방장·관리자 전용)")
public record ReportedCommentResponse(
    @Schema(description = "댓글 ID", example = "12") Long commentId,
    @Schema(description = "장소 ID", example = "3") Long placeId,
    @Schema(description = "장소 이름", example = "블루보틀 성수점") String placeName,
    @Schema(description = "작성자 ID", example = "7") Long userId,
    @Schema(description = "댓글 내용") String content,
    @Schema(description = "별점", example = "4") Integer rating,
    @Schema(description = "누적 신고 수", example = "3") Integer reportCount,
    @Schema(description = "댓글 작성 시각") LocalDateTime createdAt
) {
}
