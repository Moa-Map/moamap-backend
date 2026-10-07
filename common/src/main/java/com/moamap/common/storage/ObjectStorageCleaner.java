package com.moamap.common.storage;

import java.util.List;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

/**
 * 키 접두어 아래의 오브젝트를 모두 지운다.
 *
 * 파일 URL이 아니라 접두어로 지우는 이유: DB에는 마지막으로 저장된 URL 하나만 남지만, 실제 버킷에는 교체 전 사진이나
 * 업로드만 하고 저장하지 않은 사진도 같은 접두어 아래에 남아 있다. 사진 버킷은 공개 읽기라 이것들까지 지워야 한다.
 * 접두어는 호출부가 서버 값(회원 ID 등)으로 만든다 — 클라이언트가 보낸 URL에서 뽑지 않으므로 남의 경로를 지울 수 없다.
 */
public class ObjectStorageCleaner {

    /** S3 DeleteObjects와 ListObjectsV2가 한 번에 다루는 최대 개수와 같다. */
    private static final int BATCH_SIZE = 1000;

    private final S3Client s3Client;
    private final String bucket;

    public ObjectStorageCleaner(S3Client s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    /**
     * @param keyPrefix "profiles/42/"처럼 "/"로 끝나는 디렉터리 형태여야 한다. "profiles/4"로 부르면 profiles/42까지 지워지므로 막는다.
     * @return 지운 오브젝트 수
     */
    public int deleteAllUnder(String keyPrefix) {
        requireDirectoryPrefix(keyPrefix);
        int deleted = 0;
        String continuationToken = null;
        do {
            ListObjectsV2Response page = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(keyPrefix)
                .maxKeys(BATCH_SIZE)
                .continuationToken(continuationToken)
                .build());
            List<ObjectIdentifier> objects = page.contents().stream()
                .map(object -> ObjectIdentifier.builder().key(object.key()).build())
                .toList();
            if (!objects.isEmpty()) {
                deleteBatch(objects);
                deleted += objects.size();
            }
            continuationToken = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
        } while (continuationToken != null);
        return deleted;
    }

    /** 일괄 삭제는 일부만 실패해도 200으로 응답한다. 실패 항목이 있으면 예외로 알려 호출부가 기록하게 한다. */
    private void deleteBatch(List<ObjectIdentifier> objects) {
        DeleteObjectsResponse response = s3Client.deleteObjects(DeleteObjectsRequest.builder()
            .bucket(bucket)
            .delete(Delete.builder().objects(objects).quiet(true).build())
            .build());
        if (response.hasErrors() && !response.errors().isEmpty()) {
            throw new IllegalStateException("오브젝트 " + response.errors().size() + "개를 지우지 못했습니다.");
        }
    }

    /**
     * "profiles/42/"처럼 두 단계 이상이어야 한다. "profiles/"만 넘어오면 모든 회원의 사진이 지워지므로,
     * 호출부의 실수(회원 ID가 비어 들어온 경우 등)가 대량 삭제로 번지지 않게 여기서 막는다.
     */
    private static void requireDirectoryPrefix(String keyPrefix) {
        boolean valid = keyPrefix != null && keyPrefix.endsWith("/") && !keyPrefix.startsWith("/")
            && !keyPrefix.contains("..") && !keyPrefix.contains("//")
            && keyPrefix.chars().filter(c -> c == '/').count() >= 2;
        if (!valid) {
            throw new IllegalArgumentException("키 접두어는 'profiles/42/'처럼 두 단계 이상의 하위 경로여야 합니다.");
        }
    }
}
