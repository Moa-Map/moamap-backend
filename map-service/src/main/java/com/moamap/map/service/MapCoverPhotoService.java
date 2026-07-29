package com.moamap.map.service;

import java.util.Set;
import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.common.storage.ObjectStoragePresigner;
import com.moamap.common.storage.PresignedUploadUrl;
import com.moamap.map.dto.CoverUploadUrlRequest;
import com.moamap.map.dto.CoverUploadUrlResponse;
import com.moamap.map.exception.MapErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 지도 커버 이미지 업로드용 presigned PUT URL을 발급한다.
 *
 * 장소 사진 발급과 달리 mapId를 받지 않는다. 사용자는 "새 지도 만들기" 화면에서 지도를 만들기 전에
 * 커버를 고르므로 그 시점에는 지도가 존재하지 않는다. 발급되는 것은 스토리지에 파일 하나를 올릴 권한일 뿐이고,
 * 그 URL을 지도에 붙이려면 결국 지도 생성·수정 API의 소유권 검사를 통과해야 하므로 로그인 여부만 확인한다.
 */
@Service
public class MapCoverPhotoService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long EXPIRES_IN_SECONDS = 300L;
    // 장소 사진(places/)과 섞이지 않도록 접두사를 분리한다. 사용자별로 나눠 나중에 정리·추적이 쉽게 한다.
    private static final String KEY_PREFIX = "map-covers/";

    private final ObjectProvider<ObjectStoragePresigner> presignerProvider;

    public MapCoverPhotoService(ObjectProvider<ObjectStoragePresigner> presignerProvider) {
        this.presignerProvider = presignerProvider;
    }

    public CoverUploadUrlResponse issueUploadUrl(CoverUploadUrlRequest request, Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        checkContentType(request.contentType());
        checkFileSize(request.fileSize());

        PresignedUploadUrl presigned = presigner()
            .presign(KEY_PREFIX + userId, request.contentType(), EXPIRES_IN_SECONDS);
        return CoverUploadUrlResponse.from(presigned);
    }

    /**
     * 스토리지 설정이 없는 환경에서는 빈이 등록되지 않는다(ObjectStorageConfig 참고).
     * 이때 기동을 막는 대신, 호출 시점에 사용할 수 없음을 알린다.
     */
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
