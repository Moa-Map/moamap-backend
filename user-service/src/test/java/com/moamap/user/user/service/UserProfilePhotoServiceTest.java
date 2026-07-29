package com.moamap.user.user.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.storage.ObjectStoragePresigner;
import com.moamap.common.storage.PresignedUploadUrl;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.user.dto.ProfileUploadUrlRequest;
import com.moamap.user.user.dto.ProfileUploadUrlResponse;
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

class UserProfilePhotoServiceTest {

    private static final long USER_ID = 7L;
    private static final long ONE_MB = 1024L * 1024L;

    private final ObjectStoragePresigner presigner = mock(ObjectStoragePresigner.class);
    private final UserProfilePhotoService service = new UserProfilePhotoService(providerOf(presigner));

    @Test
    void 발급된_URL을_응답으로_그대로_전달한다() {
        givenPresigned("https://storage/put?sig=x", "profiles/7/uuid.jpg", "https://storage/profiles/7/uuid.jpg");

        ProfileUploadUrlResponse response = service.issueUploadUrl(request("image/jpeg", ONE_MB), USER_ID);

        assertThat(response.uploadUrl()).isEqualTo("https://storage/put?sig=x");
        assertThat(response.objectKey()).isEqualTo("profiles/7/uuid.jpg");
        assertThat(response.fileUrl()).isEqualTo("https://storage/profiles/7/uuid.jpg");
        assertThat(response.expiresInSeconds()).isEqualTo(300L);
    }

    @Test
    void 사용자별로_나뉜_profiles_접두사로_발급한다() {
        givenPresigned("u", "k", "f");

        service.issueUploadUrl(request("image/jpeg", ONE_MB), USER_ID);

        verify(presigner).presign(eq("profiles/7"), eq("image/jpeg"), anyLong());
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
            .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.INVALID_FILE_TYPE);

        verify(presigner, never()).presign(anyString(), anyString(), anyLong());
    }

    @Test
    void 최대_크기를_넘으면_거부한다() {
        assertThatThrownBy(() -> service.issueUploadUrl(request("image/jpeg", 10 * ONE_MB + 1), USER_ID))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.FILE_SIZE_EXCEEDED);
    }

    @Test
    void 크기가_0이하면_거부한다() {
        assertThatThrownBy(() -> service.issueUploadUrl(request("image/jpeg", 0L), USER_ID))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.FILE_SIZE_EXCEEDED);
    }

    @Test
    void 로그인하지_않으면_거부한다() {
        assertThatThrownBy(() -> service.issueUploadUrl(request("image/jpeg", ONE_MB), null))
            .isInstanceOf(BusinessException.class);

        verify(presigner, never()).presign(anyString(), anyString(), anyLong());
    }

    @Test
    void 스토리지_설정이_없으면_사용할_수_없음을_알린다() {
        UserProfilePhotoService noStorage = new UserProfilePhotoService(providerOf(null));

        assertThatThrownBy(() -> noStorage.issueUploadUrl(request("image/jpeg", ONE_MB), USER_ID))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.STORAGE_NOT_CONFIGURED);
    }

    private void givenPresigned(String uploadUrl, String objectKey, String fileUrl) {
        given(presigner.presign(anyString(), anyString(), anyLong()))
            .willReturn(new PresignedUploadUrl(uploadUrl, objectKey, fileUrl, 300L));
    }

    private ProfileUploadUrlRequest request(String contentType, long fileSize) {
        return new ProfileUploadUrlRequest(contentType, fileSize);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ObjectStoragePresigner> providerOf(ObjectStoragePresigner presigner) {
        ObjectProvider<ObjectStoragePresigner> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(presigner);
        return provider;
    }
}
