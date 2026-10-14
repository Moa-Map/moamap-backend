package com.moamap.place.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import com.moamap.place.service.PlaceWithdrawalCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 리스너가 이벤트를 정리 로직으로 넘기는지, 실패를 삼키지 않는지 확인한다.
 * 정리 규칙 자체는 PlaceWithdrawalCleanupTest가 다룬다.
 */
@ExtendWith(MockitoExtension.class)
class UserWithdrawnEventListenerTest {

    @Mock
    private PlaceWithdrawalCleanupService cleanupService;

    @InjectMocks
    private UserWithdrawnEventListener listener;

    @Test
    void 이벤트의_회원_데이터를_정리한다() {
        listener.handle(new UserWithdrawnEvent("event-1", 7L, Instant.now()));

        verify(cleanupService).cleanUp(7L);
    }

    @Test
    void 정리에_실패하면_예외를_그대로_올려_재시도와_DLQ에_맡긴다() {
        willThrow(new IllegalStateException("db down")).given(cleanupService).cleanUp(7L);

        assertThatThrownBy(() -> listener.handle(new UserWithdrawnEvent("event-1", 7L, Instant.now())))
            .isInstanceOf(IllegalStateException.class);
    }
}
