package com.moamap.map.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.storage.ObjectStoragePresigner;
import com.moamap.common.storage.PresignedUploadUrl;
import com.moamap.map.dto.CoverUploadUrlRequest;
import com.moamap.map.dto.CoverUploadUrlResponse;
import com.moamap.map.exception.MapErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 발급 전 검증 규칙과 오브젝트 키 규칙을 확인한다.
 * 실제 서명은 AWS SDK의 몫이라 presigner는 흉내 내고, 이 서비스가 책임지는 부분만 검증한다.
 */
class MapCoverPhotoServiceTest {

    private static final long USER_ID = 7L;
    private static final long ONE_MB = 1024L * 1024L;

    private final ObjectStoragePresigner presigner = mock(ObjectStoragePresigner.class);
    private final MapCoverPhotoService service = new MapCoverPhotoService(providerOf(presigner));

    @Test
    void 발급된_URL을_응답으로_그대로_전달한다() {
        givenPresigned("https://storage/put?sig=x", "map-covers/7/uuid.jpg", "https://storage/map-covers/7/uuid.jpg");

        CoverUploadUrlResponse response = service.issueUploadUrl(request("image/jpeg", ONE_MB), USER_ID);

        assertThat(response.uploadUrl()).isEqualTo("https://storage/put?sig=x");
        assertThat(response.objectKey()).isEqualTo("map-covers/7/uuid.jpg");
        assertThat(response.fileUrl()).isEqualTo("https://storage/map-covers/7/uuid.jpg");
        assertThat(response.expiresInSeconds()).isEqualTo(300L);
    }

    @Test
    void 사용자별로_나뉜_map_covers_접두사로_발급한다() {
        givenPresigned("u", "k", "f");

        service.issueUploadUrl(request("image/jpeg", ONE_MB), USER_ID);

        // 장소 사진(places/)과 섞이지 않아야 한다.
        verify(presigner).presign(eq("map-covers/7"), eq("image/jpeg"), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png", "image/webp", "IMAGE/JPEG"})
    void 허용된_형식은_대소문자와_무관하게_발급된다(String contentType) {
        givenPresigned("u", "k", "f");

        assertThat(service.issueUploadUrl(request(contentType, ONE_MB), USER_ID)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/gif", "image/svg+xml", "application/pdf", "text/html"})
    void 허용되지_않은_형식은_거부한다(String contentType) {
        assertThatThrownBy(() -> service.issueUploadUrl(request(contentType, ONE_MB), USER_ID))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.INVALID_FILE_TYPE);

        verify(presigner, never()).presign(anyString(), anyString(), anyLong());
    }

    @Test
    void 최대_크기를_넘으면_거부한다() {
        assertThatThrownBy(() -> service.issueUploadUrl(request("image/jpeg", 10 * ONE_MB + 1), USER_ID))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.FILE_SIZE_EXCEEDED);
    }

    @Test
    void 최대_크기와_같으면_발급한다() {
        givenPresigned("u", "k", "f");

        assertThat(service.issueUploadUrl(request("image/jpeg", 10 * ONE_MB), USER_ID)).isNotNull();
    }

    @Test
    void 크기가_0이하면_거부한다() {
        assertThatThrownBy(() -> service.issueUploadUrl(request("image/jpeg", 0L), USER_ID))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.FILE_SIZE_EXCEEDED);
    }

    @Test
    void 로그인하지_않으면_거부한다() {
        assertThatThrownBy(() -> service.issueUploadUrl(request("image/jpeg", ONE_MB), null))
            .isInstanceOf(BusinessException.class);

        verify(presigner, never()).presign(anyString(), anyString(), anyLong());
    }

    @Test
    void 스토리지_설정이_없으면_사용할_수_없음을_알린다() {
        MapCoverPhotoService noStorage = new MapCoverPhotoService(providerOf(null));

        assertThatThrownBy(() -> noStorage.issueUploadUrl(request("image/jpeg", ONE_MB), USER_ID))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.STORAGE_NOT_CONFIGURED);
    }

    private void givenPresigned(String uploadUrl, String objectKey, String fileUrl) {
        given(presigner.presign(anyString(), anyString(), anyLong()))
            .willReturn(new PresignedUploadUrl(uploadUrl, objectKey, fileUrl, 300L));
    }

    private CoverUploadUrlRequest request(String contentType, long fileSize) {
        return new CoverUploadUrlRequest(contentType, fileSize);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ObjectStoragePresigner> providerOf(ObjectStoragePresigner presigner) {
        ObjectProvider<ObjectStoragePresigner> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(presigner);
        return provider;
    }
}
