package com.moamap.common.storage;

import java.net.MalformedURLException;
import java.net.URL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * common 유틸 ObjectStoragePresigner가 책임지는 것만 검증한다:
 * - 오브젝트 키 생성 규칙(keyPrefix/UUID/contentType→확장자)
 * - S3Presigner에게 서명을 위임하고 그 결과(url)를 그대로 uploadUrl로 반환하는지
 * - 값 객체(PresignedUploadUrl)에 나머지 필드(fileUrl, expiresInSeconds)를 채우는지
 *
 * contentType 화이트리스트/파일 크기 검증은 이 유틸의 책임이 아니다(place-service 서비스단 책임, 청사진 3-2/step3 참고).
 * 실제 네트워크 호출은 없다(로컬 서명) — S3Presigner는 모킹한다.
 *
 * 가정(구현 계약): ObjectStoragePresigner(S3Presigner, bucket, publicBaseUrl) 생성자와
 * presign(keyPrefix, contentType, expiresInSeconds) 메서드, S3Presigner#presignPutObject(PutObjectPresignRequest)
 * (Consumer 오버로드가 아닌 명시적 request 오버로드)를 호출한다고 가정한다.
 */
@ExtendWith(MockitoExtension.class)
class ObjectStoragePresignerTest {

    private static final String BUCKET = "moamap-bucket";
    private static final String PUBLIC_BASE_URL = "https://cdn.moamap.com";

    @Mock
    private S3Presigner s3Presigner;

    private ObjectStoragePresigner presigner() {
        return new ObjectStoragePresigner(s3Presigner, BUCKET, PUBLIC_BASE_URL);
    }

    private void stubPresignedUrl(String url) throws MalformedURLException {
        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        given(presignedRequest.url()).willReturn(new URL(url));
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(presignedRequest);
    }

    @ParameterizedTest(name = "contentType={0} → 확장자={1}")
    @CsvSource({
        "image/jpeg, jpg",
        "image/png, png",
        "image/webp, webp"
    })
    void presign은_contentType에서_확장자를_도출해_objectKey를_생성한다(String contentType, String expectedExt)
            throws MalformedURLException {
        // given
        stubPresignedUrl("https://storage.example.com/signed-url");

        // when
        PresignedUploadUrl result = presigner().presign("places/10", contentType, 300L);

        // then
        assertThat(result.objectKey())
            .startsWith("places/10/")
            .endsWith("." + expectedExt);
    }

    @Test
    void presign은_keyPrefix_아래에_UUID_파일명으로_objectKey를_생성한다() throws MalformedURLException {
        // given
        stubPresignedUrl("https://storage.example.com/signed-url");

        // when
        PresignedUploadUrl result = presigner().presign("places/10", "image/jpeg", 300L);

        // then: places/10/{UUID}.jpg 형태
        assertThat(result.objectKey())
            .matches("^places/10/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg$");
    }

    @Test
    void presign은_호출할_때마다_다른_UUID로_유일한_objectKey를_생성한다() throws MalformedURLException {
        // given
        stubPresignedUrl("https://storage.example.com/signed-url");
        ObjectStoragePresigner presigner = presigner();

        // when
        PresignedUploadUrl first = presigner.presign("places/10", "image/jpeg", 300L);
        PresignedUploadUrl second = presigner.presign("places/10", "image/jpeg", 300L);

        // then
        assertThat(first.objectKey()).isNotEqualTo(second.objectKey());
    }

    @Test
    void presign은_S3Presigner가_서명한_url을_uploadUrl로_반환한다() throws MalformedURLException {
        // given
        stubPresignedUrl("https://moamap-bucket.kr1.object.ncloudstorage.com/places/10/abc.jpg?X-Amz-Signature=xyz");

        // when
        PresignedUploadUrl result = presigner().presign("places/10", "image/jpeg", 300L);

        // then
        assertThat(result.uploadUrl())
            .isEqualTo("https://moamap-bucket.kr1.object.ncloudstorage.com/places/10/abc.jpg?X-Amz-Signature=xyz");
    }

    @Test
    void presign은_공개_접근_기본URL과_objectKey를_조합한_fileUrl을_반환한다() throws MalformedURLException {
        // given
        stubPresignedUrl("https://storage.example.com/signed-url");

        // when
        PresignedUploadUrl result = presigner().presign("places/10", "image/jpeg", 300L);

        // then
        assertThat(result.fileUrl()).isEqualTo(PUBLIC_BASE_URL + "/" + result.objectKey());
    }

    @Test
    void presign은_요청받은_만료_초를_그대로_값객체에_담는다() throws MalformedURLException {
        // given
        stubPresignedUrl("https://storage.example.com/signed-url");

        // when
        PresignedUploadUrl result = presigner().presign("places/10", "image/jpeg", 300L);

        // then
        assertThat(result.expiresInSeconds()).isEqualTo(300L);
    }
}
