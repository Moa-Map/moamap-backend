package com.moamap.common.storage;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * 오브젝트 스토리지(S3 호환) presigned PUT URL 발급 유틸. 도메인 중립 — place/review/map 어떤 사진 발급에도
 * 재사용 가능하도록 place 등 특정 도메인을 모른다(키 접두어는 호출부가 넘긴다).
 *
 * contentType 화이트리스트/파일 크기 검증은 이 유틸의 책임이 아니다. 유틸은 이미 검증된 contentType이
 * 넘어온다고 가정하고 확장자만 도출한다 — 검증 실패를 도메인 ErrorCode로 던져야 하는 몫은 호출부(place-service)에 있다.
 */
public class ObjectStoragePresigner {

    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
        "image/jpeg", "jpg",
        "image/png", "png",
        "image/webp", "webp"
    );

    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String publicBaseUrl;

    public ObjectStoragePresigner(S3Presigner s3Presigner, String bucket, String publicBaseUrl) {
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
    }

    public PresignedUploadUrl presign(String keyPrefix, String contentType, long expiresInSeconds) {
        String extension = EXTENSIONS_BY_CONTENT_TYPE.get(contentType.toLowerCase());
        String objectKey = keyPrefix + "/" + UUID.randomUUID() + "." + extension;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .contentType(contentType)
            .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(expiresInSeconds))
            .putObjectRequest(putObjectRequest)
            .build();
        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        String fileUrl = publicBaseUrl + "/" + objectKey;
        return new PresignedUploadUrl(presignedRequest.url().toString(), objectKey, fileUrl, expiresInSeconds);
    }
}
