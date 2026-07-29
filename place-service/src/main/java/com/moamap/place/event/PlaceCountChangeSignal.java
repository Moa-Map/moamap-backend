package com.moamap.place.event;

/**
 * 프로세스 내부 전용 신호. 브로커와 무관하게 같은 트랜잭션 안에서 발행되고,
 * PlaceCountEventPublisher가 @TransactionalEventListener(AFTER_COMMIT)로 받아 실제 AMQP 발행으로 이어간다.
 */
public record PlaceCountChangeSignal(Long mapId) {
}
