package com.moamap.notification.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.notification.dto.NotificationResponse;
import com.moamap.notification.dto.UnreadCountResponse;
import com.moamap.notification.entity.Notification;
import com.moamap.notification.exception.NotificationErrorCode;
import com.moamap.notification.repository.NotificationRepository;
import com.moamap.notification.user.UserClient;
import com.moamap.notification.user.UserProfiles;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 알림 조회·읽음·삭제. 모든 동작이 요청한 회원이 받은 알림으로만 한정된다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    /** 한 번에 내려줄 수 있는 최대 개수. 큰 size로 전체를 한 번에 긁어가는 요청을 막는다. */
    static final int MAX_PAGE_SIZE = 50;

    /** 최신순. 같은 시각의 알림은 나중에 저장된 것이 앞에 온다. */
    private static final Sort LATEST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final NotificationRepository notificationRepository;
    private final UserClient userClient;
    private final Clock clock;

    /**
     * 정렬은 서버가 고정한다. 클라이언트가 보낸 sort는 무시한다.
     * 행동한 사람의 닉네임은 이 페이지에 나온 사람만 한 번에 묶어서 가져온다.
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(Long userId, Pageable pageable) {
        Pageable latestFirst = PageRequest.of(pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), LATEST);
        Page<Notification> notifications = notificationRepository.findByRecipientId(userId, latestFirst);

        Set<Long> actorIds = notifications.stream()
                .map(Notification::getActorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        UserProfiles profiles = userClient.findProfiles(actorIds);

        return notifications.map(notification -> NotificationResponse.of(notification, profiles));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long userId) {
        return new UnreadCountResponse(notificationRepository.countByRecipientIdAndReadAtIsNull(userId));
    }

    /** 이미 읽은 알림을 다시 읽어도 성공이다(앱이 같은 요청을 다시 보내도 안전하다). */
    public void markRead(Long userId, Long notificationId) {
        notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND))
                .markRead(clock.instant());
    }

    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, clock.instant());
    }

    public void delete(Long userId, Long notificationId) {
        if (notificationRepository.deleteByIdAndRecipientId(notificationId, userId) == 0) {
            throw new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }
}
