package com.moamap.user.auth.apple;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface AppleCredentialRepository extends JpaRepository<AppleCredential, Long> {

    /**
     * 탈퇴 직후 바로 폐기할 행을 잠근다. 재시도 배치가 같은 행을 잡고 있으면 건너뛴다(skip locked).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select c from AppleCredential c where c.userId = :userId and c.revokeRequestedAt is not null")
    Optional<AppleCredential> findPendingRevocation(@Param("userId") Long userId);

    /**
     * 재시도할 때가 된 폐기 대기 행. 인스턴스가 여러 개여도 같은 행을 두 번 잡지 않도록 skip locked로 가져온다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select c from AppleCredential c
            where c.revokeRequestedAt is not null and c.revokeAttempts < :maxAttempts and c.nextRevokeAt <= :now
            order by c.nextRevokeAt""")
    List<AppleCredential> findDueRevocations(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts,
                                             Limit limit);
}
