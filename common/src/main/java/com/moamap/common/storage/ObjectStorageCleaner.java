package com.moamap.common.storage;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

/**
 * 저장된 파일 URL로 오브젝트를 지운다. ObjectStoragePresigner가 만든 URL(publicBaseUrl + "/" + key)의 역방향이다.
 *
 * 파일 URL은 클라이언트가 보내온 값이 그대로 저장된 것이라 믿을 수 없다. 그래서 두 가지를 확인한 뒤에만 지운다.
 * - 우리 버킷 주소로 시작하는가 — 카카오 프로필 사진처럼 외부 URL이면 건드리지 않는다.
 * - 호출부가 지정한 키 접두어 아래에 있는가 — 남의 파일 URL을 자기 것처럼 저장해 두고 삭제를 유도하는 걸 막는다.
 */
public class ObjectStorageCleaner {

    private final S3Client s3Client;
    private final String bucket;
    private final String publicBaseUrl;

    public ObjectStorageCleaner(S3Client s3Client, String bucket, String publicBaseUrl) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * @param fileUrl         DB에 저장된 파일 URL
     * @param ownedKeyPrefix  이 호출자가 지울 권한이 있는 키 접두어. 반드시 "/"로 끝나야 한다(예: "profiles/42/").
     * @return 실제로 삭제 요청을 보냈으면 true, 조건에 맞지 않아 건너뛰었으면 false
     */
    public boolean deleteIfOwned(String fileUrl, String ownedKeyPrefix) {
        if (ownedKeyPrefix == null || !ownedKeyPrefix.endsWith("/")) {
            throw new IllegalArgumentException("키 접두어는 '/'로 끝나야 다른 사용자의 경로와 겹치지 않는다.");
        }
        String objectKey = objectKeyOf(fileUrl);
        if (objectKey == null || !objectKey.startsWith(ownedKeyPrefix)) {
            return false;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        return true;
    }

    /** 우리 버킷 URL이 아니거나 경로 조작이 섞여 있으면 null. */
    private String objectKeyOf(String fileUrl) {
        String base = publicBaseUrl + "/";
        if (fileUrl == null || !fileUrl.startsWith(base)) {
            return null;
        }
        String objectKey = fileUrl.substring(base.length());
        boolean tampered = objectKey.isEmpty() || objectKey.startsWith("/") || objectKey.contains("..")
                || objectKey.contains("?") || objectKey.contains("#") || objectKey.contains("\\");
        return tampered ? null : objectKey;
    }
}
