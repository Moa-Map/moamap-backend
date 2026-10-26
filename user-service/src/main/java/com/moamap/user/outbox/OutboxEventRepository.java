package com.moamap.user.outbox;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.QueryHints;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * 아직 발행되지 않은 이벤트를 오래된 순으로 가져온다.
     *
     * 인스턴스를 여러 개 띄우면 폴러도 여러 개가 동시에 도는데, 잠긴 행은 건너뛰게 해서(skip locked)
     * 같은 이벤트를 두 서버가 동시에 집지 않도록 한다. 별도의 분산 락을 두지 않아도 되는 이유다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.id")
    List<OutboxEvent> findUnpublished(Limit limit);

    /**
     * 발행이 끝난 오래된 기록을 지운다. 두지 않으면 테이블이 계속 커진다.
     */
    @Modifying
    @Query("delete from OutboxEvent e where e.publishedAt is not null and e.publishedAt < :threshold")
    int deletePublishedBefore(@Param("threshold") Instant threshold);
}
