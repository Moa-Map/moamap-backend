package com.moamap.user.outbox;

import java.time.Instant;
import com.moamap.user.event.UserRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이벤트가 보관함에 제대로 남는지, 발행 대상 조회가 의도대로 동작하는지 확인한다.
 * 트랜잭션 밖에서 호출하면 막히는지도 함께 본다 — 이게 뚫리면 Outbox의 원자성 전제가 깨진다.
 */
@DataJpaTest
// 운영과 같은 ObjectMapper(JavaTimeModule 포함)를 쓰도록 Boot의 Jackson 설정을 그대로 올린다.
@Import({OutboxRecorder.class, JacksonAutoConfiguration.class})
class OutboxRecorderTest {

    private static final String USER_ID = "1";

    @Autowired
    private OutboxRecorder outboxRecorder;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @Transactional
    void 이벤트를_미발행_상태로_저장한다() {
        outboxRecorder.record(USER_ID, UserRegisteredEvent.TYPE, event());

        OutboxEvent saved = outboxEventRepository.findAll().get(0);
        assertThat(saved.getAggregateId()).isEqualTo(USER_ID);
        assertThat(saved.getEventType()).isEqualTo("user.registered");
        assertThat(saved.getPublishedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @Transactional
    void payload를_JSON으로_직렬화해_저장한다() {
        outboxRecorder.record(USER_ID, UserRegisteredEvent.TYPE, event());

        // 소비자가 그대로 역직렬화할 수 있어야 하므로 필드가 담겨 있어야 한다.
        assertThat(outboxEventRepository.findAll().get(0).getPayload())
            .contains("\"userId\":1")
            .contains("eventId");
    }

    @Test
    // @DataJpaTest는 각 테스트를 트랜잭션으로 감싸므로, 밖에서 부르는 상황을 만들려면 그 트랜잭션을 걷어내야 한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 트랜잭션_밖에서_호출하면_거부한다() {
        // 도메인 변경과 같은 트랜잭션이 아니면 원자성이 보장되지 않는다. 조용히 어긋나는 대신 즉시 실패해야 한다.
        assertThatThrownBy(() -> outboxRecorder.record(USER_ID, UserRegisteredEvent.TYPE, event()))
            .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    @Transactional
    void 발행되지_않은_건만_오래된_순으로_가져온다() {
        outboxRecorder.record("1", UserRegisteredEvent.TYPE, event());
        outboxRecorder.record("2", UserRegisteredEvent.TYPE, event());
        outboxEventRepository.flush();

        outboxEventRepository.findAll().get(0).markPublished();
        outboxEventRepository.flush();

        assertThat(outboxEventRepository.findUnpublished(Limit.of(10)))
            .hasSize(1)
            .extracting(OutboxEvent::getAggregateId)
            .containsExactly("2");
    }

    @Test
    @Transactional
    void 가져올_개수를_제한할_수_있다() {
        outboxRecorder.record("1", UserRegisteredEvent.TYPE, event());
        outboxRecorder.record("2", UserRegisteredEvent.TYPE, event());
        outboxEventRepository.flush();

        assertThat(outboxEventRepository.findUnpublished(Limit.of(1))).hasSize(1);
    }

    private UserRegisteredEvent event() {
        return new UserRegisteredEvent("evt-1", 1L, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
