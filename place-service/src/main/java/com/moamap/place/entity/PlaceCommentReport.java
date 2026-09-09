package com.moamap.place.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 댓글 신고 이력.
 *
 * 한 사람이 같은 댓글을 여러 번 신고해 수를 부풀리지 못하도록 (댓글, 신고자) 조합에 유니크 제약을 둔다.
 * 신고 행은 지우지 않는다 — 나중에 자동 처리 기준(임계치)을 정할 때 근거 데이터가 된다.
 */
@Entity
@Table(
    name = "place_comment_reports",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_place_comment_reports_comment_reporter",
        columnNames = {"place_comment_id", "reporter_id"}),
    indexes = @Index(name = "idx_place_comment_reports_comment", columnList = "place_comment_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceCommentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_comment_id", nullable = false)
    private Long placeCommentId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommentReportReason reason;

    /** 사유만으로 부족할 때 신고자가 덧붙이는 설명. 선택 입력이다. */
    @Column(length = 200)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private PlaceCommentReport(Long placeCommentId, Long reporterId, CommentReportReason reason, String detail) {
        this.placeCommentId = placeCommentId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.detail = detail;
        this.createdAt = LocalDateTime.now();
    }

    public static PlaceCommentReport of(Long placeCommentId, Long reporterId,
            CommentReportReason reason, String detail) {
        return new PlaceCommentReport(placeCommentId, reporterId, reason, detail);
    }
}
