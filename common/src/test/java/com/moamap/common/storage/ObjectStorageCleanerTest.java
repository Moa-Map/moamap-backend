package com.moamap.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@ExtendWith(MockitoExtension.class)
class ObjectStorageCleanerTest {

    private static final String BUCKET = "moamap-photos";
    private static final String PUBLIC_BASE_URL = "https://photos.example.com";

    @Mock
    private S3Client s3Client;

    private ObjectStorageCleaner cleaner() {
        return new ObjectStorageCleaner(s3Client, BUCKET, PUBLIC_BASE_URL);
    }

    @Test
    void 우리_버킷의_허용된_경로면_해당_키를_삭제한다() {
        boolean deleted = cleaner().deleteIfOwned(PUBLIC_BASE_URL + "/profiles/42/a.jpg", "profiles/42/");

        assertThat(deleted).isTrue();
        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo("profiles/42/a.jpg");
    }

    @Test
    void 외부_URL은_건드리지_않는다() {
        // 가입할 때 받아온 카카오 프로필 사진은 우리가 지울 수도, 지울 필요도 없다.
        boolean deleted = cleaner().deleteIfOwned("https://k.kakaocdn.net/dn/profile.jpg", "profiles/42/");

        assertThat(deleted).isFalse();
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void 다른_사용자의_경로는_지우지_않는다() {
        // 남의 사진 URL을 자기 프로필로 저장해 두고 탈퇴해도 남의 파일은 지워지지 않아야 한다.
        boolean deleted = cleaner().deleteIfOwned(PUBLIC_BASE_URL + "/profiles/7/a.jpg", "profiles/42/");

        assertThat(deleted).isFalse();
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void 사용자_ID가_앞자리만_같은_경로는_지우지_않는다() {
        // 접두어를 "/"로 끝내지 않으면 profiles/4 가 profiles/42 를 포함해 버린다.
        boolean deleted = cleaner().deleteIfOwned(PUBLIC_BASE_URL + "/profiles/42/a.jpg", "profiles/4/");

        assertThat(deleted).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/profiles/42/../7/a.jpg",
        "//profiles/42/a.jpg",
        "/profiles/42/a.jpg?versionId=1",
        "/profiles/42/a.jpg#x",
        "/profiles/42\\..\\7\\a.jpg",
        "/"
    })
    void 경로_조작이_섞인_URL은_지우지_않는다(String path) {
        boolean deleted = cleaner().deleteIfOwned(PUBLIC_BASE_URL + path, "profiles/42/");

        assertThat(deleted).isFalse();
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "https://photos.example.com.evil.com/profiles/42/a.jpg"})
    void 비어_있거나_비슷하게_생긴_다른_호스트면_지우지_않는다(String url) {
        assertThat(cleaner().deleteIfOwned(url, "profiles/42/")).isFalse();
    }

    @Test
    void 접두어가_슬래시로_끝나지_않으면_호출_실수로_보고_거부한다() {
        assertThatThrownBy(() -> cleaner().deleteIfOwned(PUBLIC_BASE_URL + "/profiles/42/a.jpg", "profiles/42"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
