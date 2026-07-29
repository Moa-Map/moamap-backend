package com.moamap.map.event;

import java.time.Instant;
import com.moamap.map.service.MapService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 리스너가 이벤트를 지도 생성으로 넘기는지, 실패를 삼키지 않는지 확인한다.
 * 생성 규칙 자체는 PersonalMapCreationTest가 다룬다.
 */
@ExtendWith(MockitoExtension.class)
class UserRegisteredEventListenerTest {

    private static final String MAP_NAME = "나만의 지도";

    @Mock
    private MapService mapService;

    @InjectMocks
    private UserRegisteredEventListener listener;

    @Test
    void 이벤트의_사용자에게_지도를_만든다() {
        givenMapName();

        listener.handle(event(1L));

        verify(mapService).createPersonalMapIfAbsent(1L, MAP_NAME);
    }

    @Test
    void 같은_이벤트가_다시_와도_생성을_그대로_위임한다() {
        givenMapName();

        listener.handle(event(1L));
        listener.handle(event(1L));

        // 중복 처리를 막는 책임은 생성 쪽에 있다. 리스너는 걸러내지 않고 그대로 넘긴다.
        verify(mapService, times(2)).createPersonalMapIfAbsent(1L, MAP_NAME);
    }

    @Test
    void 생성에_실패하면_예외를_그대로_올려보낸다() {
        givenMapName();
        willThrow(new IllegalStateException("DB 오류"))
            .given(mapService).createPersonalMapIfAbsent(anyLong(), anyString());

        // 여기서 잡아버리면 메시지가 성공 처리돼 사라진다. 재시도와 DLQ가 동작하도록 그대로 던져야 한다.
        assertThatThrownBy(() -> listener.handle(event(1L)))
            .isInstanceOf(IllegalStateException.class);
    }

    private void givenMapName() {
        ReflectionTestUtils.setField(listener, "personalMapName", MAP_NAME);
    }

    private UserRegisteredEvent event(long userId) {
        return new UserRegisteredEvent("evt-1", userId, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
