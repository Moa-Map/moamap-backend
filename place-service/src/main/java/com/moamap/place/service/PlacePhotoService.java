package com.moamap.place.service;

import java.util.Set;
import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.common.storage.ObjectStoragePresigner;
import com.moamap.common.storage.PresignedUploadUrl;
import com.moamap.place.dto.PhotoUploadUrlRequest;
import com.moamap.place.dto.PhotoUploadUrlResponse;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import org.springframework.stereotype.Service;

/**
 * 장소 사진 업로드 presigned URL 발급. 발급 권한 = 장소 등록 권한(청사진 3-1, PlaceService.resolveInitialStatus와 동일 규칙)이라
 * PENDING/APPROVED 구분 없이 "등록 가능한 멤버인가"만 본다.
 */
@Service
public class PlacePhotoService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long EXPIRES_IN_SECONDS = 300L;
    private static final String KEY_PREFIX = "places/";

    private final MapClient mapClient;
    private final ObjectStoragePresigner objectStoragePresigner;

    public PlacePhotoService(MapClient mapClient, ObjectStoragePresigner objectStoragePresigner) {
        this.mapClient = mapClient;
        this.objectStoragePresigner = objectStoragePresigner;
    }

    public PhotoUploadUrlResponse issueUploadUrl(PhotoUploadUrlRequest request, Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        MapMemberResponse memberInfo = mapClient.getMemberInfo(request.mapId(), userId);
        checkRegistrable(memberInfo);
        checkContentType(request.contentType());
        checkFileSize(request.fileSize());

        PresignedUploadUrl presigned = objectStoragePresigner.presign(
            KEY_PREFIX + request.mapId(), request.contentType(), EXPIRES_IN_SECONDS);
        return new PhotoUploadUrlResponse(
            presigned.uploadUrl(), presigned.objectKey(), presigned.fileUrl(), presigned.expiresInSeconds());
    }

    // 사진 발급 권한 판단은 장소 등록 권한 판단(PlaceService.resolveInitialStatus)과 동일 규칙이다(청사진 3-1).
    private void checkRegistrable(MapMemberResponse memberInfo) {
        if (memberInfo.role() == MapMemberRole.NONE) {
            throw new BusinessException(PlaceErrorCode.NOT_MAP_MEMBER);
        }
        if (memberInfo.mapType() == MapType.OFFICIAL) {
            throw new BusinessException(PlaceErrorCode.OFFICIAL_MAP_NOT_REGISTRABLE);
        }
    }

    private void checkContentType(String contentType) {
        if (!ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(PlaceErrorCode.INVALID_FILE_TYPE);
        }
    }

    private void checkFileSize(long fileSize) {
        if (fileSize <= 0 || fileSize > MAX_FILE_SIZE) {
            throw new BusinessException(PlaceErrorCode.FILE_SIZE_EXCEEDED);
        }
    }
}
