package com.moamap.place.dto;

import com.moamap.place.entity.PlaceReportTargetType;
import com.moamap.place.entity.ReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 운영자가 보는 장소·장소 댓글 신고 한 건.
 *
 * 신고된 내용을 함께 담는다. 운영자는 프라이빗 지도의 멤버가 아니어서 일반 API로는 내용을 볼 수 없기 때문이다.
 */
public record PlaceReportAdminResponse(
    Long reportId,
    PlaceReportTargetType targetType,
    Long targetId,
    Long placeId,
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
     * @param title     장소 이름 (댓글은 그 댓글이 달린 장소 이름)
     * @param text      장소 메모, 댓글 본문
     * @param imageUrls 장소 사진, 댓글 사진
     * @param authorId  장소 등록자, 댓글 작성자
     */
    public record Content(boolean deleted, String title, String text, List<String> imageUrls, Long authorId) {

        public static Content missing() {
            return new Content(true, null, null, List.of(), null);
        }
    }
}
