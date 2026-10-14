package com.moamap.user.notice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공지사항. 관리자가 등록하고 앱 사용자 누구나 읽는다.
 *
 * 본문은 일반 텍스트다. 앱은 HTML로 그리지 않고 텍스트로 표시하므로 스크립트가 끼어들 여지가 없다.
 * 삭제는 소프트 삭제다(팀 규칙). 목록·상세 조회는 deleted_at이 비어 있는 행만 본다.
 */
@Entity
@Table(name = "notice", indexes = @Index(name = "idx_notice_deleted_created", columnList = "deleted_at, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 등록한 관리자 ID. */
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Notice(String title, String content, Long createdBy) {
        this.title = title;
        this.content = content;
        this.createdBy = createdBy;
    }

    public static Notice create(String title, String content, Long createdBy) {
        return new Notice(title, content, createdBy);
    }

    /** 보낸 값만 바꾼다. null이면 기존 값을 유지한다. */
    public void update(String title, String content) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
    }

    public void delete(Instant at) {
        this.deletedAt = at;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
