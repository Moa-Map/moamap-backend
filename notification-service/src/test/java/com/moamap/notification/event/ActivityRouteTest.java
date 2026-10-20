package com.moamap.notification.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.moamap.notification.entity.NotificationType;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ActivityRouteTest {

    @Test
    void 라우팅_키는_서로_겹치지_않는다() {
        assertThat(Arrays.stream(ActivityRoute.values()).map(ActivityRoute::getRoutingKey)).doesNotHaveDuplicates();
    }

    @Test
    void 모든_알림_종류는_정확히_하나의_이벤트에서_만들어진다() {
        assertThat(Arrays.stream(ActivityRoute.values()).map(ActivityRoute::getNotificationType))
                .containsExactlyInAnyOrder(NotificationType.values());
    }

    @Test
    void 기존_서비스_이벤트와_라우팅_키가_겹치지_않는다() {
        // 같은 익스체인지에 이미 흐르는 이벤트(장소 수 변경, 가입, 탈퇴)가 알림 큐로 들어오면 안 된다.
        assertThat(Arrays.stream(ActivityRoute.values()).map(ActivityRoute::getRoutingKey))
                .doesNotContain("place.count.changed", "user.registered", "user.withdrawn");
    }
}
