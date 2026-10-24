package com.moamap.place.dto;

import com.moamap.place.entity.ReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 방장·관리자가 보는 신고된 장소 한 곳. 신고자는 담지 않는다(보복 방지).
 */
public record ReportedPlaceResponse(
    Long placeId,
    String name,
    @Schema(description = "대표 사진. 없으면 비어 있다.")
    String photoUrl,
    @Schema(description = "쌓인 신고 수", example = "3")
    long reportCount,
    @Schema(description = "사유별 신고 수", example = "{\"FALSE_INFO\": 2, \"SPAM\": 1}")
    Map<ReportReason, Long> reasons,
    LocalDateTime lastReportedAt
) {
}
