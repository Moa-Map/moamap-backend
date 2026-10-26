package com.moamap.map.event;

import java.time.Instant;
import com.moamap.map.repository.MapEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 브로커 없이 리스너 메서드를 직접 호출해 청사진 3-1 (다) 표의 각 행을 검증한다.
 * 역직렬화 실패·재시도 자체는 Spring AMQP 프레임워크(Jackson2JsonMessageConverter의
 * TypePrecedence, retry interceptor)가 처리하는 영역이라, 여기서는 리스너가 예외를
 * 삼키지 않고 그대로 던져 프레임워크가 재시도·DLQ 처리를 이어갈 수 있게 하는지만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class PlaceCountEventListenerTest {

    @Mock
    private MapEntityRepository mapEntityRepository;

    private PlaceCountEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PlaceCountEventListener(mapEntityRepository);
    }

    @Test
    void mapId가_존재하면_placeCount를_갱신하고_정상_종료한다() {
        PlaceCountChangedEvent event = new PlaceCountChangedEvent("event-1", 10L, 7L, Instant.now());
        when(mapEntityRepository.updatePlaceCount(10L, 7L)).thenReturn(1);

        assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();

        verify(mapEntityRepository).updatePlaceCount(10L, 7L);
    }

    @Test
    void mapId에_해당하는_지도가_없으면_0행이어도_예외_없이_정상_종료한다() {
        PlaceCountChangedEvent event = new PlaceCountChangedEvent("event-2", 999L, 3L, Instant.now());
        when(mapEntityRepository.updatePlaceCount(999L, 3L)).thenReturn(0);

        assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();
    }

    @Test
    void DB_오류등_일시_실패는_예외를_삼키지_않고_그대로_전파해_프레임워크가_재시도할_수_있게_한다() {
        PlaceCountChangedEvent event = new PlaceCountChangedEvent("event-4", 10L, 5L, Instant.now());
        when(mapEntityRepository.updatePlaceCount(10L, 5L))
            .thenThrow(new DataAccessResourceFailureException("DB 연결 실패"));

        assertThatThrownBy(() -> listener.handle(event))
            .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
