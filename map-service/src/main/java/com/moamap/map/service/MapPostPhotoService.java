package com.moamap.map.service;

import java.util.Set;
import com.moamap.common.exception.BusinessException;
import com.moamap.common.storage.ObjectStoragePresigner;
import com.moamap.common.storage.PresignedUploadUrl;
import com.moamap.map.dto.MapPostPhotoUploadUrlRequest;
import com.moamap.map.dto.MapPostPhotoUploadUrlResponse;
import com.moamap.map.exception.MapErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 로그 탭 게시물 사진 업로드용 presigned PUT URL 발급. 발급 권한은 게시물 작성 권한과 같다.
 * 한 장씩 발급받아 게시물 작성 요청의 imageUrls에 최대 5장까지 담는다(개수 제한은 요청 DTO가 검증).
 */
@Service
public class MapPostPhotoService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long EXPIRES_IN_SECONDS = 300L;
    // 지도 커버(map-covers/)·장소 사진(places/)과 섞이지 않게 접두사를 분리한다.
    private static final String KEY_PREFIX = "map-posts/";

    private final ObjectProvider<ObjectStoragePresigner> presignerProvider;
    private final MapPostAccessPolicy accessPolicy;

    public MapPostPhotoService(ObjectProvider<ObjectStoragePresigner> presignerProvider,
            MapPostAccessPolicy accessPolicy) {
        this.presignerProvider = presignerProvider;
        this.accessPolicy = accessPolicy;
    }

    public MapPostPhotoUploadUrlResponse issueUploadUrl(Long mapId, MapPostPhotoUploadUrlRequest request,
            Long userId) {
        accessPolicy.requireWritable(mapId, userId);
        checkContentType(request.contentType());
        checkFileSize(request.fileSize());

        PresignedUploadUrl presigned = presigner()
            .presign(KEY_PREFIX + mapId, request.contentType(), EXPIRES_IN_SECONDS);
        return MapPostPhotoUploadUrlResponse.from(presigned);
    }

    /** 스토리지 설정이 없는 환경에서는 빈이 등록되지 않는다(ObjectStorageConfig 참고). */
    private ObjectStoragePresigner presigner() {
        ObjectStoragePresigner presigner = presignerProvider.getIfAvailable();
        if (presigner == null) {
            throw new BusinessException(MapErrorCode.STORAGE_NOT_CONFIGURED);
        }
        return presigner;
    }

    private void checkContentType(String contentType) {
        if (!ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(MapErrorCode.INVALID_FILE_TYPE);
        }
    }

    private void checkFileSize(long fileSize) {
        if (fileSize <= 0 || fileSize > MAX_FILE_SIZE) {
            throw new BusinessException(MapErrorCode.FILE_SIZE_EXCEEDED);
        }
    }
}
