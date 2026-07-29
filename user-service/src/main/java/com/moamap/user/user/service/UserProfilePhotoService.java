package com.moamap.user.user.service;

import java.util.Set;
import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.common.storage.ObjectStoragePresigner;
import com.moamap.common.storage.PresignedUploadUrl;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.user.dto.ProfileUploadUrlRequest;
import com.moamap.user.user.dto.ProfileUploadUrlResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 프로필 이미지 업로드용 presigned PUT URL을 발급한다. 이미지는 1장만 허용한다.
 */
@Service
public class UserProfilePhotoService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long EXPIRES_IN_SECONDS = 300L;
    private static final String KEY_PREFIX = "profiles/";

    private final ObjectProvider<ObjectStoragePresigner> presignerProvider;

    public UserProfilePhotoService(ObjectProvider<ObjectStoragePresigner> presignerProvider) {
        this.presignerProvider = presignerProvider;
    }

    public ProfileUploadUrlResponse issueUploadUrl(ProfileUploadUrlRequest request, Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        checkContentType(request.contentType());
        checkFileSize(request.fileSize());

        PresignedUploadUrl presigned = presigner()
            .presign(KEY_PREFIX + userId, request.contentType(), EXPIRES_IN_SECONDS);
        return ProfileUploadUrlResponse.from(presigned);
    }

    /**
     * 스토리지 설정이 없는 환경에서는 빈이 등록되지 않는다(ObjectStorageConfig 참고).
     * 이때 기동을 막는 대신, 호출 시점에 사용할 수 없음을 알린다.
     */
    private ObjectStoragePresigner presigner() {
        ObjectStoragePresigner presigner = presignerProvider.getIfAvailable();
        if (presigner == null) {
            throw new BusinessException(UserErrorCode.STORAGE_NOT_CONFIGURED);
        }
        return presigner;
    }

    private void checkContentType(String contentType) {
        if (!ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(UserErrorCode.INVALID_FILE_TYPE);
        }
    }

    private void checkFileSize(long fileSize) {
        if (fileSize <= 0 || fileSize > MAX_FILE_SIZE) {
            throw new BusinessException(UserErrorCode.FILE_SIZE_EXCEEDED);
        }
    }
}
