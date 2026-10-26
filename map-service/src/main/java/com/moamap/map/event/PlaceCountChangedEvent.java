package com.moamap.map.event;

import java.time.Instant;

/**
 * place-service가 place.events 익스체인지(라우팅 키 place.count.changed)로 발행하는 AMQP payload.
 * place-service의 동명 record와 필드명·타입이 동일해야 한다(청사진 4장 — 이벤트 계약은 JSON 스키마).
 * eventId는 dedupe용이 아니라 로그 상관관계·DLQ 추적용이고, placeCount는 델타가 아니라
 * 발행 시점의 절대 개수다(청사진 2-2).
 */
public record PlaceCountChangedEvent(String eventId, Long mapId, long placeCount, Instant occurredAt) {
}
