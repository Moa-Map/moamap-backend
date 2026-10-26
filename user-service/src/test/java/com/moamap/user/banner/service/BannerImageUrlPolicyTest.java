package com.moamap.user.banner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.moamap.user.config.ObjectStorageProperties;
import com.moamap.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 배너 이미지 주소는 요청 본문으로 들어온다. 업로드 주소 발급 때 만든 형태만 통과해야 한다.
 */
class BannerImageUrlPolicyTest {

    private static final String BASE = "https://photos.example.com";
    private static final String UUID = "3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b";

    @Test
    void 배너_업로드_경로의_이미지는_통과한다() {
        String url = BASE + "/banners/" + UUID + ".jpg";

        assertThat(policy().requireBannerImage(url)).isEqualTo(url);
        assertThat(policy().requireBannerImage(BASE + "/banners/" + UUID + ".png")).isNotNull();
        assertThat(policy().requireBannerImage(BASE + "/banners/" + UUID + ".webp")).isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "https://evil.example.org/banners/" + UUID + ".jpg",            // 외부 사이트
        "https://photos.example.com.evil.org/banners/" + UUID + ".jpg", // 비슷하게 생긴 다른 호스트
        BASE + "/profiles/42/" + UUID + ".jpg",                         // 다른 용도로 올린 사진
        BASE + "/banners/../profiles/42/a.jpg",                         // 경로 조작
        BASE + "/banners/" + UUID + ".jpg?x=1",                         // 쿼리 덧붙이기
        BASE + "/banners/" + UUID + ".svg",                             // SVG는 스크립트를 담을 수 있다
        BASE + "/banners/" + UUID + ".gif",                             // 허용하지 않은 형식
        BASE + "/banners/sub/" + UUID + ".jpg",                         // 하위 경로
        BASE + "/banners/not-a-uuid.jpg",                               // 발급 형태가 아님
        "http://photos.example.com/banners/" + UUID + ".jpg"            // 다른 스킴
    })
    void 업로드_주소로_올린_형태가_아니면_거부한다(String url) {
        assertThatThrownBy(() -> policy().requireBannerImage(url))
                .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_BANNER_IMAGE);
    }

    @Test
    void 스토리지_설정이_없으면_사용할_수_없다고_알린다() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ObjectStorageProperties> none = mock(ObjectProvider.class);

        assertThatThrownBy(() -> new BannerImageUrlPolicy(none).requireBannerImage(BASE + "/banners/" + UUID + ".jpg"))
                .extracting("errorCode").isEqualTo(UserErrorCode.STORAGE_NOT_CONFIGURED);
    }

    private static BannerImageUrlPolicy policy() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ObjectStorageProperties> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(new ObjectStorageProperties("ap-northeast-2", "test-bucket", BASE));
        return new BannerImageUrlPolicy(provider);
    }
}
