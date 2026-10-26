package com.moamap.user.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발행할 이벤트를 보관함에 남긴다.
 *
 * 반드시 도메인 변경과 같은 트랜잭션 안에서 호출해야 한다. 그래야 "회원은 저장됐는데 이벤트는 없는" 상태가 생기지 않는다.
 * 실수로 트랜잭션 밖에서 부르면 조용히 어긋나는 대신 바로 예외가 나도록 MANDATORY로 둔다.
 */
@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String aggregateId, String eventType, Object payload) {
        outboxEventRepository.save(OutboxEvent.pending(aggregateId, eventType, serialize(eventType, payload)));
    }

    private String serialize(String eventType, Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // 직렬화가 안 되는 payload는 코드 결함이다. 저장해봐야 발행 시 다시 실패하므로 여기서 끊는다.
            throw new IllegalArgumentException("이벤트를 직렬화할 수 없습니다: " + eventType, e);
        }
    }
}
