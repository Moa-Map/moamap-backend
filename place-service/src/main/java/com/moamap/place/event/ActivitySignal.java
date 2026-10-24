package com.moamap.place.event;

/**
 * 알림 대상이 되는 일이 일어났다는 프로세스 내부 신호. 같은 트랜잭션 안에서 발행되고,
 * ActivityEventPublisher가 커밋 이후에 받아 place.events로 보낸다. 롤백되면 알림도 나가지 않는다.
 *
 * 라우팅 키는 notification-service의 ActivityRoute와 같은 값이어야 한다.
 */
public record ActivitySignal(String routingKey, Long recipientId, Long actorId, Long targetId, Long mapId) {

    public static final String PLACE_APPROVED = "place.approved";
    public static final String PLACE_REJECTED = "place.rejected";
    public static final String PLACE_COMMENTED = "place.commented";
}
