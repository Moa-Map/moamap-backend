package com.moamap.map.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지도·로그·로그 댓글 신고. 운영자만 본다.
 *
 * 한 사람이 같은 대상을 여러 번 신고해 수를 부풀리지 못하도록 (대상 종류, 대상, 신고자)를 유일하게 둔다.
 * 신고 행은 지우지 않는다 — 운영자가 판단할 근거다.
 */
@Entity
@Table(name = "map_content_reports",
    uniqueConstraints = @UniqueConstraint(name = "uk_map_content_reports_target_reporter",
        columnNames = {"target_type", "target_id", "reporter_id"}),
    indexes = @Index(name = "idx_map_content_reports_created", columnList = "created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapContentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = EnumNameConverters.ReportTargetTypeConverter.class)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** 대상이 속한 지도. 운영자가 어느 지도의 일인지 바로 알 수 있게 둔다. */
    @Column(name = "map_id", nullable = false)
    private Long mapId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Convert(converter = EnumNameConverters.ReportReasonConverter.class)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    /** 사유만으로 부족할 때 신고자가 덧붙이는 설명. */
    @Column(length = 200)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private MapContentReport(ReportTargetType targetType, Long targetId, Long mapId, Long reporterId,
                             ReportReason reason, String detail) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.mapId = mapId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.detail = detail;
    }

    public static MapContentReport of(ReportTargetType targetType, Long targetId, Long mapId, Long reporterId,
                                      ReportReason reason, String detail) {
        return new MapContentReport(targetType, targetId, mapId, reporterId, reason, detail);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
