package com.moamap.notification.service;

import static com.moamap.notification.support.NotificationFixtures.NOW;
import static com.moamap.notification.support.NotificationFixtures.notification;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.moamap.common.exception.BusinessException;
import com.moamap.notification.dto.ActorResponse;
import com.moamap.notification.dto.NotificationResponse;
import com.moamap.notification.entity.Notification;
import com.moamap.notification.entity.NotificationType;
import com.moamap.notification.exception.NotificationErrorCode;
import com.moamap.notification.repository.NotificationRepository;
import com.moamap.notification.support.FixedClockConfig;
import com.moamap.notification.user.UserClient;
import com.moamap.notification.user.UserProfiles;
import com.moamap.notification.user.dto.UserProfileResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({NotificationService.class, FixedClockConfig.class})
class NotificationServiceTest {

    private static final Long ME = 1L;
    private static final Long OTHER = 2L;
    private static final Long ACTOR = 3L;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private UserClient userClient;

    @Test
    void 내_알림을_최신순으로_돌려준다() {
        Notification older = notificationRepository.save(notification(ME, NOW.minusSeconds(60)));
        Notification newer = notificationRepository.save(notification(ME, NOW));
        notificationRepository.save(notification(OTHER, NOW));
        given(userClient.findProfiles(any())).willReturn(UserProfiles.of(List.of()));

        // 클라이언트가 보낸 정렬은 무시한다.
        Page<NotificationResponse> page = notificationService.getNotifications(ME,
                PageRequest.of(0, 20, Sort.by("createdAt").ascending()));

        assertThat(page.getContent()).extracting(NotificationResponse::notificationId)
                .containsExactly(newer.getId(), older.getId());
    }

    @Test
    void 한_번에_최대_50개까지만_돌려준다() {
        given(userClient.findProfiles(any())).willReturn(UserProfiles.of(List.of()));

        Page<NotificationResponse> page = notificationService.getNotifications(ME, PageRequest.of(0, 1000));

        assertThat(page.getSize()).isEqualTo(NotificationService.MAX_PAGE_SIZE);
    }

    @Test
    void 행동한_사람의_닉네임으로_문구를_만든다() {
        notificationRepository.save(notification(ME, NotificationType.PLACE_COMMENTED, ACTOR, NOW));
        given(userClient.findProfiles(any())).willReturn(UserProfiles.of(List.of(
                new UserProfileResponse(ACTOR, "모아", "https://cdn.example/profile.jpg"))));

        NotificationResponse response = onlyNotificationOf(ME);

        assertThat(response.message()).isEqualTo("모아님이 내 장소에 댓글을 남겼어요.");
        assertThat(response.actor()).isEqualTo(new ActorResponse(ACTOR, "모아", "https://cdn.example/profile.jpg"));
    }

    @Test
    void 탈퇴한_회원은_ID_없이_탈퇴한_사용자로_보인다() {
        notificationRepository.save(notification(ME, NotificationType.PLACE_COMMENTED, ACTOR, NOW));
        given(userClient.findProfiles(any())).willReturn(UserProfiles.of(List.of()));

        NotificationResponse response = onlyNotificationOf(ME);

        assertThat(response.actor()).isEqualTo(new ActorResponse(null, "탈퇴한 사용자", null));
        assertThat(response.message()).isEqualTo("탈퇴한 사용자님이 내 장소에 댓글을 남겼어요.");
    }

    @Test
    void 회원_정보를_가져오지_못하면_탈퇴로_보이지_않고_닉네임_없는_문구를_쓴다() {
        notificationRepository.save(notification(ME, NotificationType.PLACE_COMMENTED, ACTOR, NOW));
        given(userClient.findProfiles(any())).willReturn(UserProfiles.unavailable());

        NotificationResponse response = onlyNotificationOf(ME);

        assertThat(response.actor()).isEqualTo(new ActorResponse(ACTOR, null, null));
        assertThat(response.message()).isEqualTo("내 장소에 새 댓글이 달렸어요.");
    }

    @Test
    void 행동한_사람이_없는_알림은_actor가_비어_있다() {
        notificationRepository.save(notification(ME, NotificationType.PLACE_APPROVED, ACTOR, NOW));
        given(userClient.findProfiles(any())).willReturn(UserProfiles.of(List.of()));

        NotificationResponse response = onlyNotificationOf(ME);

        assertThat(response.actor()).isNull();
        assertThat(response.message()).isEqualTo("등록 요청한 장소가 승인됐어요.");
    }

    @Test
    void 안_읽은_알림_수는_내_것만_센다() {
        notificationRepository.save(notification(ME, NOW));
        Notification read = notification(ME, NOW);
        read.markRead(NOW);
        notificationRepository.save(read);
        notificationRepository.save(notification(OTHER, NOW));

        assertThat(notificationService.getUnreadCount(ME).unreadCount()).isEqualTo(1);
    }

    @Test
    void 내_알림을_읽음으로_바꾼다() {
        Notification mine = notificationRepository.save(notification(ME, NOW.minusSeconds(60)));

        notificationService.markRead(ME, mine.getId());

        assertThat(notificationRepository.findById(mine.getId()).orElseThrow().getReadAt()).isEqualTo(NOW);
    }

    @Test
    void 이미_읽은_알림을_다시_읽어도_처음_읽은_시각을_유지한다() {
        Instant firstRead = NOW.minusSeconds(30);
        Notification mine = notification(ME, NOW.minusSeconds(60));
        mine.markRead(firstRead);
        notificationRepository.save(mine);

        notificationService.markRead(ME, mine.getId());

        assertThat(notificationRepository.findById(mine.getId()).orElseThrow().getReadAt()).isEqualTo(firstRead);
    }

    @Test
    void 남의_알림을_읽으려_하면_없는_알림과_같이_404다() {
        Notification others = notificationRepository.save(notification(OTHER, NOW));

        assertThatThrownBy(() -> notificationService.markRead(ME, others.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        assertThat(notificationRepository.findById(others.getId()).orElseThrow().isRead()).isFalse();
    }

    @Test
    void 모두_읽음은_내_알림만_바꾼다() {
        notificationRepository.save(notification(ME, NOW));
        notificationRepository.save(notification(OTHER, NOW));

        notificationService.markAllRead(ME);

        assertThat(notificationRepository.countByRecipientIdAndReadAtIsNull(ME)).isZero();
        assertThat(notificationRepository.countByRecipientIdAndReadAtIsNull(OTHER)).isEqualTo(1);
    }

    @Test
    void 내_알림을_지운다() {
        Notification mine = notificationRepository.save(notification(ME, NOW));

        notificationService.delete(ME, mine.getId());

        assertThat(notificationRepository.existsById(mine.getId())).isFalse();
    }

    @Test
    void 남의_알림을_지우려_하면_404이고_지워지지_않는다() {
        Notification others = notificationRepository.save(notification(OTHER, NOW));

        assertThatThrownBy(() -> notificationService.delete(ME, others.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        assertThat(notificationRepository.existsById(others.getId())).isTrue();
    }

    private NotificationResponse onlyNotificationOf(Long userId) {
        List<NotificationResponse> content = notificationService.getNotifications(userId, PageRequest.of(0, 20)).getContent();
        assertThat(content).hasSize(1);
        return content.get(0);
    }
}
