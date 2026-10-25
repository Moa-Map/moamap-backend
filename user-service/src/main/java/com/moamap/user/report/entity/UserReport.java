package com.moamap.user.report.entity;

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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자(프로필) 신고. 운영자만 본다.
 *
 * 한 사람이 같은 사용자를 여러 번 신고해 수를 부풀리지 못하도록 (대상, 신고자) 조합을 유일하게 둔다.
 * 신고 행은 지우지 않는다 — 운영자가 판단할 근거다.
 */
@Entity
@Table(name = "user_report",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_report_target_reporter",
                columnNames = {"target_user_id", "reporter_id"}),
        indexes = @Index(name = "idx_user_report_created", columnList = "created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Convert(converter = ReportReasonConverter.class)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    /** 사유만으로 부족할 때 신고자가 덧붙이는 설명. */
    @Column(length = 200)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private UserReport(Long targetUserId, Long reporterId, ReportReason reason, String detail) {
        this.targetUserId = targetUserId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.detail = detail;
    }

    public static UserReport of(Long targetUserId, Long reporterId, ReportReason reason, String detail) {
        return new UserReport(targetUserId, reporterId, reason, detail);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
