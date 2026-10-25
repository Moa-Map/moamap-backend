package com.moamap.map.dto;

import java.time.LocalDateTime;
import java.util.List;
import com.moamap.map.entity.MapContentReport;
import com.moamap.map.entity.ReportReason;
import com.moamap.map.entity.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영자가 보는 지도·로그·로그 댓글 신고 한 건.
 *
 * 신고된 내용을 함께 담는다. 운영자는 프라이빗 지도의 멤버가 아니어서 일반 API로는 내용을 볼 수 없기 때문이다.
 */
public record MapReportAdminResponse(
    Long reportId,
    ReportTargetType targetType,
    Long targetId,
    Long mapId,
    Long reporterId,
    ReportReason reason,
    String detail,
    LocalDateTime reportedAt,
    @Schema(description = "이 대상에 쌓인 신고 수")
    long targetReportCount,
    Content content
) {

    /**
     * 신고된 내용. 이미 지워졌으면 deleted가 true이고 나머지는 비어 있을 수 있다.
     *
     * @param title    지도 이름 (로그·댓글은 비어 있음)
     * @param text     지도 소개, 로그 본문, 댓글 본문
     * @param imageUrls 지도 커버 사진, 로그 사진
     * @param authorId 지도 방장, 글쓴이
     */
    public record Content(boolean deleted, String title, String text, List<String> imageUrls, Long authorId) {

        public static Content missing() {
            return new Content(true, null, null, List.of(), null);
        }
    }

    public static MapReportAdminResponse of(MapContentReport report, long targetReportCount, Content content) {
        return new MapReportAdminResponse(report.getId(), report.getTargetType(), report.getTargetId(),
            report.getMapId(), report.getReporterId(), report.getReason(), report.getDetail(), report.getCreatedAt(),
            targetReportCount, content);
    }
}
