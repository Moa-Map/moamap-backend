package com.moamap.notification.event;

import com.moamap.notification.entity.NotificationType;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 어떤 서비스의 어떤 이벤트가 어떤 알림이 되는지. 큐 바인딩과 수신 시 종류 판별이 모두 이 표 하나를 따른다.
 *
 * 새 알림을 추가할 때는 NotificationType에 종류를 더하고 여기에 한 줄을 더하면 된다.
 * 발행하는 서비스는 알림을 몰라도 된다 — 자기 익스체인지에 이 라우팅 키로 일어난 일을 보내기만 한다.
 */
@Getter
@RequiredArgsConstructor
public enum ActivityRoute {

    PLACE_APPROVED(Exchanges.PLACE, "place.approved", NotificationType.PLACE_APPROVED),
    PLACE_REJECTED(Exchanges.PLACE, "place.rejected", NotificationType.PLACE_REJECTED),
    PLACE_COMMENTED(Exchanges.PLACE, "place.commented", NotificationType.PLACE_COMMENTED),
    MAP_POST_COMMENTED(Exchanges.MAP, "map.post.commented", NotificationType.MAP_POST_COMMENTED),
    MAP_ADMIN_ASSIGNED(Exchanges.MAP, "map.admin.assigned", NotificationType.MAP_ADMIN_ASSIGNED),
    MAP_OWNER_TRANSFERRED(Exchanges.MAP, "map.owner.transferred", NotificationType.MAP_OWNER_TRANSFERRED),
    INQUIRY_ANSWERED(Exchanges.USER, "inquiry.answered", NotificationType.INQUIRY_ANSWERED),
    PLACE_REPORTED(Exchanges.PLACE, "place.reported", NotificationType.PLACE_REPORTED),
    // 운영자 신고 알림은 신고를 받은 서비스마다 발행한다. 알림 종류는 하나지만 익스체인지별로 라우팅 키를 나눈다.
    PLACE_REPORT_RECEIVED(Exchanges.PLACE, "place.report.received", NotificationType.REPORT_RECEIVED),
    MAP_REPORT_RECEIVED(Exchanges.MAP, "map.report.received", NotificationType.REPORT_RECEIVED),
    USER_REPORT_RECEIVED(Exchanges.USER, "user.report.received", NotificationType.REPORT_RECEIVED);

    private final String exchange;
    private final String routingKey;
    private final NotificationType notificationType;

    public static Optional<ActivityRoute> fromRoutingKey(String routingKey) {
        return Arrays.stream(values())
                .filter(route -> route.routingKey.equals(routingKey))
                .findFirst();
    }

    /** 각 서비스가 발행하는 익스체인지 이름. 발행하는 쪽과 같은 이름·속성(durable topic)이어야 한다. */
    public static final class Exchanges {

        public static final String PLACE = "place.events";
        public static final String MAP = "map.events";
        public static final String USER = "user.events";

        private Exchanges() {
        }
    }
}
