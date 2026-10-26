package com.moamap.user.banner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moamap.common.exception.CommonErrorCode;
import com.moamap.user.banner.dto.AdminBannerResponse;
import com.moamap.user.banner.dto.BannerImageUploadUrlRequest;
import com.moamap.user.banner.dto.BannerResponse;
import com.moamap.user.config.ObjectStorageProperties;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.user.entity.Role;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import com.moamap.user.user.service.AdminGuard;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 배너 등록·순서·삭제와 관리자 권한을 실제 DB로 확인한다.
 */
@DataJpaTest
@Import({BannerService.class, BannerAdminService.class, AdminGuard.class, BannerImageUrlPolicy.class,
        BannerServiceTest.StorageConfig.class})
class BannerServiceTest {

    private static final String BASE = "https://photos.example.com";

    @Autowired private BannerService bannerService;
    @Autowired private BannerAdminService bannerAdminService;
    @Autowired private UserRepository userRepository;

    private Long adminId;
    private Long userId;

    @BeforeEach
    void setUp() {
        adminId = user("admin", Role.ADMIN).getId();
        userId = user("member", Role.USER).getId();
    }

    @Test
    void 새_배너는_맨_뒤에_붙고_앱에는_순서대로_나온다() {
        String first = image();
        String second = image();
        bannerAdminService.create(adminId, first);
        bannerAdminService.create(adminId, second);

        assertThat(bannerService.getBanners()).extracting(BannerResponse::imageUrl).containsExactly(first, second);
    }

    @Test
    void 배너는_10개까지만_등록된다() {
        for (int i = 0; i < BannerAdminService.MAX_BANNERS; i++) {
            bannerAdminService.create(adminId, image());
        }

        assertThatThrownBy(() -> bannerAdminService.create(adminId, image()))
                .extracting("errorCode").isEqualTo(UserErrorCode.BANNER_LIMIT_EXCEEDED);
    }

    @Test
    void 삭제한_배너는_개수에서_빠져_다시_등록할_수_있다() {
        Long removed = null;
        for (int i = 0; i < BannerAdminService.MAX_BANNERS; i++) {
            removed = bannerAdminService.create(adminId, image()).bannerId();
        }
        bannerAdminService.delete(adminId, removed);

        assertThat(bannerAdminService.create(adminId, image()).bannerId()).isNotNull();
    }

    @Test
    void 삭제한_배너는_앱과_관리자_목록에서_사라진다() {
        Long bannerId = bannerAdminService.create(adminId, image()).bannerId();

        bannerAdminService.delete(adminId, bannerId);

        assertThat(bannerService.getBanners()).isEmpty();
        assertThat(bannerAdminService.getBanners(adminId)).isEmpty();
        assertThatThrownBy(() -> bannerAdminService.delete(adminId, bannerId))
                .extracting("errorCode").isEqualTo(UserErrorCode.BANNER_NOT_FOUND);
    }

    @Test
    void 순서를_바꾸면_앱에도_그_순서로_나온다() {
        Long a = bannerAdminService.create(adminId, image()).bannerId();
        Long b = bannerAdminService.create(adminId, image()).bannerId();
        Long c = bannerAdminService.create(adminId, image()).bannerId();

        List<AdminBannerResponse> reordered = bannerAdminService.reorder(adminId, List.of(c, a, b));

        assertThat(reordered).extracting(AdminBannerResponse::bannerId).containsExactly(c, a, b);
        assertThat(bannerService.getBanners()).extracting(BannerResponse::bannerId).containsExactly(c, a, b);
    }

    @Test
    void 순서_변경에서_빠지거나_중복되거나_없는_배너가_있으면_전체를_거부한다() {
        Long a = bannerAdminService.create(adminId, image()).bannerId();
        Long b = bannerAdminService.create(adminId, image()).bannerId();

        for (List<Long> invalid : List.of(List.of(a), List.of(a, a, b), List.of(a, b, 999_999L))) {
            assertThatThrownBy(() -> bannerAdminService.reorder(adminId, invalid))
                    .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_BANNER_ORDER);
        }
        // 거부된 요청은 순서를 건드리지 않는다.
        assertThat(bannerService.getBanners()).extracting(BannerResponse::bannerId).containsExactly(a, b);
    }

    @Test
    void 이미지를_교체해도_순서는_그대로다() {
        Long a = bannerAdminService.create(adminId, image()).bannerId();
        Long b = bannerAdminService.create(adminId, image()).bannerId();
        String replaced = image();

        bannerAdminService.changeImage(adminId, a, replaced);

        assertThat(bannerService.getBanners()).extracting(BannerResponse::bannerId).containsExactly(a, b);
        assertThat(bannerService.getBanners().get(0).imageUrl()).isEqualTo(replaced);
    }

    @Test
    void 외부_이미지_주소로는_등록할_수_없다() {
        assertThatThrownBy(() -> bannerAdminService.create(adminId, "https://evil.example.org/a.jpg"))
                .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_BANNER_IMAGE);
    }

    @Test
    void 일반_회원은_배너를_관리할_수_없다() {
        assertThatThrownBy(() -> bannerAdminService.create(userId, image()))
                .extracting("errorCode").isEqualTo(CommonErrorCode.ACCESS_DENIED);
        assertThatThrownBy(() -> bannerAdminService.getBanners(userId))
                .extracting("errorCode").isEqualTo(CommonErrorCode.ACCESS_DENIED);
        assertThatThrownBy(() -> bannerAdminService.issueImageUploadUrl(userId, new BannerImageUploadUrlRequest("image/jpeg", 100L)))
                .extracting("errorCode").isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    void 관리자가_아니면_없는_배너라도_404가_아니라_403이다() {
        assertThatThrownBy(() -> bannerAdminService.delete(userId, 999_999L))
                .extracting("errorCode").isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    void 업로드_주소는_허용한_형식과_크기만_발급한다() {
        assertThatThrownBy(() -> bannerAdminService.issueImageUploadUrl(adminId, new BannerImageUploadUrlRequest("image/svg+xml", 100L)))
                .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_FILE_TYPE);
        assertThatThrownBy(() -> bannerAdminService.issueImageUploadUrl(adminId, new BannerImageUploadUrlRequest("image/jpeg", 10L * 1024 * 1024 + 1)))
                .extracting("errorCode").isEqualTo(UserErrorCode.FILE_SIZE_EXCEEDED);
    }

    @Test
    void 스토리지가_없는_환경에서는_업로드_주소를_발급할_수_없다고_알린다() {
        // 테스트 컨텍스트에는 presigner 빈이 없다(스토리지 미설정 환경과 같다).
        assertThatThrownBy(() -> bannerAdminService.issueImageUploadUrl(adminId, new BannerImageUploadUrlRequest("image/jpeg", 100L)))
                .extracting("errorCode").isEqualTo(UserErrorCode.STORAGE_NOT_CONFIGURED);
    }

    private static String image() {
        return BASE + "/banners/" + UUID.randomUUID() + ".jpg";
    }

    private User user(String providerId, Role role) {
        User user = User.createSocialUser("kakao", providerId, "닉네임", null, null);
        ReflectionTestUtils.setField(user, "role", role);
        return userRepository.saveAndFlush(user);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StorageConfig {
        @Bean
        ObjectStorageProperties objectStorageProperties() {
            return new ObjectStorageProperties("ap-northeast-2", "test-bucket", BASE);
        }
    }
}
