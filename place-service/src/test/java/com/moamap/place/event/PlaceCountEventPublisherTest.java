package com.moamap.place.event;

import com.moamap.place.entity.PlaceStatus;
import com.moamap.place.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 청사진 3-3(가) 5~7단계: publishNow가 현재 절대값을 조회해 발행하고,
 * 조회/발행 어느 쪽에서 예외가 나든 삼켜서 호출자(원 요청)에 영향을 주지 않는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PlaceCountEventPublisherTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private PlaceCountEventPublisher publisher;

    @Test
    void publishNow는_현재_count를_조회해서_place_events_익스체인지로_발행한다() {
        // given
        given(placeRepository.countByMapIdAndStatusAndDeletedAtIsNull(10L, PlaceStatus.APPROVED)).willReturn(7L);

        // when
        publisher.publishNow(10L);

        // then
        ArgumentCaptor<PlaceCountChangedEvent> captor = ArgumentCaptor.forClass(PlaceCountChangedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("place.events"), eq("place.count.changed"), captor.capture());
        PlaceCountChangedEvent event = captor.getValue();
        assertThat(event.mapId()).isEqualTo(10L);
        assertThat(event.placeCount()).isEqualTo(7L);
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void publishNow는_count_조회_중_예외가_나도_전파하지_않는다() {
        // given
        given(placeRepository.countByMapIdAndStatusAndDeletedAtIsNull(10L, PlaceStatus.APPROVED))
            .willThrow(new RuntimeException("DB 커넥션 실패"));

        // when & then
        assertThatCode(() -> publisher.publishNow(10L)).doesNotThrowAnyException();
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void publishNow는_AmqpException이_나도_전파하지_않는다() {
        // given
        given(placeRepository.countByMapIdAndStatusAndDeletedAtIsNull(10L, PlaceStatus.APPROVED)).willReturn(3L);
        willThrow(new AmqpConnectException(new RuntimeException("connection refused")))
            .given(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        // when & then
        assertThatCode(() -> publisher.publishNow(10L)).doesNotThrowAnyException();
    }
}
