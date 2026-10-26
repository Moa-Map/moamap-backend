package com.moamap.notification.service;

import com.moamap.notification.config.NotificationProperties;
import com.moamap.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 더 이상 필요 없는 알림을 지운다. 탈퇴한 회원이 받은 알림과, 보관 기간이 지난 알림이다.
 *
 * 둘 다 같은 조건으로 다시 지워도 결과가 같아서, 이벤트가 다시 오거나 서버 여러 대가 동시에 돌아도 안전하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationCleanupService {

    private final NotificationRepository notificationRepository;
    private final NotificationProperties properties;
    private final Clock clock;

    public int deleteAllReceivedBy(Long userId) {
        return notificationRepository.deleteAllByRecipientId(userId);
    }

    /** 배포 이미지의 기본 시간대는 UTC라, 사용자가 적은 새벽에 돌도록 한국 시간으로 고정한다. */
    @Scheduled(cron = "${notification.cleanup-cron}", zone = "Asia/Seoul")
    public int deleteExpired() {
        Instant threshold = clock.instant().minus(Duration.ofDays(properties.retentionDays()));
        int deleted = notificationRepository.deleteCreatedBefore(threshold);
        log.info("보관 기간이 지난 알림 정리 완료. deleted={}, before={}", deleted, threshold);
        return deleted;
    }
}
