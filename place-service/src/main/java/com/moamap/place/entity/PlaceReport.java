package com.moamap.place.entity;

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
 * 장소 신고. 운영자와 그 지도의 방장·관리자가 본다(방장·관리자에게는 신고자를 보여주지 않는다).
 *
 * 한 사람이 같은 장소를 여러 번 신고해 수를 부풀리지 못하도록 (장소, 신고자)를 유일하게 둔다.
 * 신고 행은 지우지 않는다 — 판단의 근거다.
 */
@Entity
@Table(name = "place_reports",
    uniqueConstraints = @UniqueConstraint(name = "uk_place_reports_place_reporter", columnNames = {"place_id", "reporter_id"}),
    indexes = {
        @Index(name = "idx_place_reports_map_created", columnList = "map_id, created_at"),
        @Index(name = "idx_place_reports_created", columnList = "created_at")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    /** 장소가 속한 지도. 방장·관리자가 자기 지도의 신고만 모아 보는 데 쓴다. */
    @Column(name = "map_id", nullable = false)
    private Long mapId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Convert(converter = ReportReasonConverter.class)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    /** 사유만으로 부족할 때 신고자가 덧붙이는 설명. */
    @Column(length = 200)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private PlaceReport(Long placeId, Long mapId, Long reporterId, ReportReason reason, String detail) {
        this.placeId = placeId;
        this.mapId = mapId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.detail = detail;
    }

    public static PlaceReport of(Long placeId, Long mapId, Long reporterId, ReportReason reason, String detail) {
        return new PlaceReport(placeId, mapId, reporterId, reason, detail);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
