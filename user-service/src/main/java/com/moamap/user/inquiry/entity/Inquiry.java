package com.moamap.user.inquiry.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 사용자 문의. 관리자만 열람한다.
 *
 * 답변 이메일과 본문은 개인정보다. 관리자 API로만 내보내고, 로그에 남기지 않으며,
 * 회원이 탈퇴하면 그 회원의 문의는 행째 지운다(UserWithdrawalWriter).
 */
@Entity
@Table(name = "inquiry", indexes = {
        @Index(name = "idx_inquiry_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_inquiry_status_created", columnList = "status, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryType type;

    @Column(name = "reply_email", nullable = false, length = 254)
    private String replyEmail;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 답변 완료로 바꾼 시각. 답변 완료가 아니면 비어 있다. */
    @Column(name = "answered_at")
    private Instant answeredAt;

    private Inquiry(Long userId, InquiryType type, String replyEmail, String content) {
        this.userId = userId;
        this.type = type;
        this.replyEmail = replyEmail;
        this.content = content;
        this.status = InquiryStatus.RECEIVED;
    }

    public static Inquiry receive(Long userId, InquiryType type, String replyEmail, String content) {
        return new Inquiry(userId, type, replyEmail, content);
    }

    /** 답변 완료일 때만 답변 시각을 남긴다. 다른 상태로 되돌리면 비운다. */
    public void changeStatus(InquiryStatus status, Instant at) {
        this.status = status;
        this.answeredAt = status == InquiryStatus.ANSWERED ? at : null;
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
