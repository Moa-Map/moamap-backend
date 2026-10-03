package com.moamap.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Object;

@ExtendWith(MockitoExtension.class)
class ObjectStorageCleanerTest {

    private static final String BUCKET = "moamap-photos";

    @Mock
    private S3Client s3Client;

    private ObjectStorageCleaner cleaner() {
        return new ObjectStorageCleaner(s3Client, BUCKET);
    }

    @Test
    void 접두어_아래_오브젝트를_모두_지운다() {
        // 지금 프로필 사진뿐 아니라 교체 전 사진도 같은 경로에 남아 있다.
        given(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
            .willReturn(page(false, null, "profiles/42/old.jpg", "profiles/42/current.jpg"));
        given(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).willReturn(DeleteObjectsResponse.builder().build());

        int deleted = cleaner().deleteAllUnder("profiles/42/");

        assertThat(deleted).isEqualTo(2);
        ArgumentCaptor<ListObjectsV2Request> list = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(list.capture());
        assertThat(list.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(list.getValue().prefix()).isEqualTo("profiles/42/");
        ArgumentCaptor<DeleteObjectsRequest> delete = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(delete.capture());
        assertThat(delete.getValue().delete().objects()).extracting(ObjectIdentifier::key)
            .containsExactly("profiles/42/old.jpg", "profiles/42/current.jpg");
    }

    @Test
    void 결과가_여러_페이지면_끝까지_지운다() {
        given(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
            .willReturn(page(true, "next", "profiles/42/a.jpg"))
            .willReturn(page(false, null, "profiles/42/b.jpg"));
        given(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).willReturn(DeleteObjectsResponse.builder().build());

        int deleted = cleaner().deleteAllUnder("profiles/42/");

        assertThat(deleted).isEqualTo(2);
        ArgumentCaptor<ListObjectsV2Request> list = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client, times(2)).listObjectsV2(list.capture());
        assertThat(list.getAllValues().get(1).continuationToken()).isEqualTo("next");
    }

    @Test
    void 지울_게_없으면_삭제_요청을_보내지_않는다() {
        given(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).willReturn(page(false, null));

        assertThat(cleaner().deleteAllUnder("profiles/42/")).isZero();
        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void 일부만_지워지면_예외로_알린다() {
        given(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).willReturn(page(false, null, "profiles/42/a.jpg"));
        given(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).willReturn(DeleteObjectsResponse.builder()
            .errors(S3Error.builder().key("profiles/42/a.jpg").code("AccessDenied").build())
            .build());

        assertThatThrownBy(() -> cleaner().deleteAllUnder("profiles/42/")).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"profiles/42", "/", "/profiles/42/", "profiles/../7/"})
    void 디렉터리_형태가_아닌_접두어는_거부한다(String prefix) {
        // "profiles/4"를 허용하면 profiles/42, profiles/43…까지 지워진다.
        assertThatThrownBy(() -> cleaner().deleteAllUnder(prefix)).isInstanceOf(IllegalArgumentException.class);
        verify(s3Client, never()).listObjectsV2(any(ListObjectsV2Request.class));
    }

    private static ListObjectsV2Response page(boolean truncated, String nextToken, String... keys) {
        List<S3Object> contents = java.util.Arrays.stream(keys).map(key -> S3Object.builder().key(key).build()).toList();
        return ListObjectsV2Response.builder()
            .contents(contents)
            .isTruncated(truncated)
            .nextContinuationToken(nextToken)
            .build();
    }
}
