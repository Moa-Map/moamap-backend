package com.moamap.user.outbox;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 발행할 이벤트를 도메인 변경과 같은 트랜잭션에 저장해두는 보관함.
 *
 * 커밋 직후에 곧바로 브로커로 보내면, 커밋은 됐는데 발행이 실패하는 구간이 생긴다.
 * 그 사이 브로커가 죽거나 서버가 재시작되면 이벤트가 사라지고 되돌릴 방법이 없다.
 * 이벤트를 DB에 함께 남겨두면 커밋된 이상 반드시 존재하고, 발행 실패는 폴러가 다시 시도한다.
 */
@Entity
@Table(
    name = "outbox_event",
    indexes = @Index(name = "idx_outbox_unpublished", columnList = "published_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이벤트가 속한 대상의 식별자(예: userId). 추적·디버깅용. */
    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    /** 발행 시 라우팅 키로도 쓰인다(예: user.registered). */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** null이면 아직 발행되지 않았다는 뜻. 폴러가 이 값으로 대상을 고른다. */
    @Column(name = "published_at")
    private Instant publishedAt;

    private OutboxEvent(String aggregateId, String eventType, String payload) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public static OutboxEvent pending(String aggregateId, String eventType, String payload) {
        return new OutboxEvent(aggregateId, eventType, payload);
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }
}
