package com.moamap.user.block.entity;

import jakarta.persistence.Column;
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
 * 차단 관계. 한쪽 방향이다 — 차단한 사람에게만 차단된 사람의 콘텐츠가 보이지 않고, 상대는 차단 사실을 모른다.
 *
 * 해제하면 행을 지운다. 차단 이력은 운영자 신고(사유 BLOCKED)로 따로 남는다.
 */
@Entity
@Table(name = "user_block",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_block_blocker_blocked", columnNames = {"blocker_id", "blocked_id"}),
        indexes = @Index(name = "idx_user_block_blocked", columnList = "blocked_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_id", nullable = false)
    private Long blockerId;

    @Column(name = "blocked_id", nullable = false)
    private Long blockedId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private UserBlock(Long blockerId, Long blockedId) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
    }

    public static UserBlock of(Long blockerId, Long blockedId) {
        return new UserBlock(blockerId, blockedId);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
