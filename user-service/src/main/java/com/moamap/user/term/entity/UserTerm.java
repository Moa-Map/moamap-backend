package com.moamap.user.term.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 약관 한 줄(버전)에 동의한 기록. 누가·언제·어떤 버전에 동의했는지만 남긴다(IP·기기 정보 없음).
 *
 * 쌓기만 하고 고치지 않는다. 회원이 탈퇴하면 그 회원의 기록을 지운다(UserWithdrawalWriter).
 */
@Entity
@Table(name = "user_term",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_term_user_term", columnNames = {"user_id", "term_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "term_id", nullable = false)
    private Long termId;

    /** 동의한 시각. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private UserTerm(Long userId, Long termId) {
        this.userId = userId;
        this.termId = termId;
    }

    public static UserTerm agree(Long userId, Long termId) {
        return new UserTerm(userId, termId);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
