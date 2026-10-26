package com.moamap.notification.repository;

import com.moamap.notification.entity.Notification;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientId(Long recipientId, Pageable pageable);

    /** 남의 알림은 없는 것과 똑같이 보이도록 받는 사람까지 함께 조건으로 건다. */
    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    boolean existsBySourceEventId(String sourceEventId);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :readAt where n.recipientId = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") Long recipientId, @Param("readAt") Instant readAt);

    @Modifying(clearAutomatically = true)
    @Query("delete from Notification n where n.id = :id and n.recipientId = :recipientId")
    int deleteByIdAndRecipientId(@Param("id") Long id, @Param("recipientId") Long recipientId);

    @Modifying(clearAutomatically = true)
    @Query("delete from Notification n where n.recipientId = :recipientId")
    int deleteAllByRecipientId(@Param("recipientId") Long recipientId);

    @Modifying(clearAutomatically = true)
    @Query("delete from Notification n where n.createdAt < :threshold")
    int deleteCreatedBefore(@Param("threshold") Instant threshold);
}
