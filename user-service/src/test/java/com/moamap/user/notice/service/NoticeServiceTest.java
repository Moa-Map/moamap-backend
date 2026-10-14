package com.moamap.user.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.notice.dto.NoticeCreateRequest;
import com.moamap.user.notice.dto.NoticeResponse;
import com.moamap.user.notice.dto.NoticeUpdateRequest;
import com.moamap.user.user.entity.Role;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import com.moamap.user.user.service.AdminGuard;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 공지 조회·관리를 실제 DB로 확인한다. 정렬, 삭제 필터, 관리자 권한 순서를 본다.
 */
@DataJpaTest
@Import({NoticeService.class, NoticeAdminService.class, AdminGuard.class})
class NoticeServiceTest {

    @Autowired private NoticeService noticeService;
    @Autowired private NoticeAdminService noticeAdminService;
    @Autowired private UserRepository userRepository;

    private Long adminId;
    private Long userId;

    @BeforeEach
    void setUp() {
        adminId = user("admin", Role.ADMIN).getId();
        userId = user("member", Role.USER).getId();
    }

    @Test
    void 등록한_공지는_최신순으로_조회된다() {
        noticeAdminService.create(adminId, new NoticeCreateRequest("첫 공지", "본문1"));
        noticeAdminService.create(adminId, new NoticeCreateRequest("둘째 공지", "본문2"));

        Page<NoticeResponse> page = noticeService.getNotices(PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(NoticeResponse::title).containsExactly("둘째 공지", "첫 공지");
        assertThat(page.getContent().get(0).content()).isEqualTo("본문2");
    }

    @Test
    void 클라이언트가_보낸_정렬은_무시하고_최신순을_지킨다() {
        noticeAdminService.create(adminId, new NoticeCreateRequest("A", "본문"));
        noticeAdminService.create(adminId, new NoticeCreateRequest("B", "본문"));

        Page<NoticeResponse> page = noticeService.getNotices(PageRequest.of(0, 20, Sort.by("title")));

        assertThat(page.getContent()).extracting(NoticeResponse::title).containsExactly("B", "A");
    }

    @Test
    void 한_번에_내려주는_개수는_최대_50개다() {
        Page<NoticeResponse> page = noticeService.getNotices(PageRequest.of(0, 1000));

        assertThat(page.getSize()).isEqualTo(NoticeService.MAX_PAGE_SIZE);
    }

    @Test
    void 공지가_없으면_빈_목록이다() {
        assertThat(noticeService.getNotices(PageRequest.of(0, 20)).getContent()).isEmpty();
    }

    @Test
    void 삭제한_공지는_목록과_상세에서_사라진다() {
        Long noticeId = noticeAdminService.create(adminId, new NoticeCreateRequest("지울 공지", "본문")).noticeId();

        noticeAdminService.delete(adminId, noticeId);

        assertThat(noticeService.getNotices(PageRequest.of(0, 20)).getContent()).isEmpty();
        assertThatThrownBy(() -> noticeService.getNotice(noticeId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.NOTICE_NOT_FOUND);
    }

    @Test
    void 이미_삭제한_공지는_다시_수정하거나_삭제할_수_없다() {
        Long noticeId = noticeAdminService.create(adminId, new NoticeCreateRequest("공지", "본문")).noticeId();
        noticeAdminService.delete(adminId, noticeId);

        assertThatThrownBy(() -> noticeAdminService.delete(adminId, noticeId))
                .extracting("errorCode").isEqualTo(UserErrorCode.NOTICE_NOT_FOUND);
        assertThatThrownBy(() -> noticeAdminService.update(adminId, noticeId, new NoticeUpdateRequest("새 제목", null)))
                .extracting("errorCode").isEqualTo(UserErrorCode.NOTICE_NOT_FOUND);
    }

    @Test
    void 수정은_보낸_값만_바꾼다() {
        Long noticeId = noticeAdminService.create(adminId, new NoticeCreateRequest("원래 제목", "원래 본문")).noticeId();

        NoticeResponse updated = noticeAdminService.update(adminId, noticeId, new NoticeUpdateRequest("바뀐 제목", null));

        assertThat(updated.title()).isEqualTo("바뀐 제목");
        assertThat(updated.content()).isEqualTo("원래 본문");
        assertThat(updated.updatedAt()).isAfterOrEqualTo(updated.createdAt());
    }

    @Test
    void 일반_회원은_공지를_등록할_수_없다() {
        assertThatThrownBy(() -> noticeAdminService.create(userId, new NoticeCreateRequest("공지", "본문")))
                .extracting("errorCode").isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    void 관리자가_아니면_없는_공지라도_404가_아니라_403이다() {
        // 권한을 먼저 본다. 404를 주면 관리자가 아닌 사람이 공지 ID 존재 여부를 떠볼 수 있다.
        assertThatThrownBy(() -> noticeAdminService.delete(userId, 999_999L))
                .extracting("errorCode").isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    void 탈퇴한_관리자는_남은_토큰으로도_공지를_관리할_수_없다() {
        User admin = userRepository.findById(adminId).orElseThrow();
        admin.withdraw(Instant.now());
        userRepository.saveAndFlush(admin);

        assertThatThrownBy(() -> noticeAdminService.create(adminId, new NoticeCreateRequest("공지", "본문")))
                .extracting("errorCode").isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    void 없는_회원은_관리자가_아니다() {
        assertThatThrownBy(() -> noticeAdminService.create(888_888L, new NoticeCreateRequest("공지", "본문")))
                .extracting("errorCode").isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    private User user(String providerId, Role role) {
        User user = User.createSocialUser("kakao", providerId, "닉네임", null, null);
        ReflectionTestUtils.setField(user, "role", role);
        return userRepository.saveAndFlush(user);
    }
}
