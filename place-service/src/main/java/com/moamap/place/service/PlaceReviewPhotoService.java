package com.moamap.place.service;

import java.util.Set;
import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.common.storage.ObjectStoragePresigner;
import com.moamap.common.storage.PresignedUploadUrl;
import com.moamap.place.dto.ReviewPhotoUploadUrlRequest;
import com.moamap.place.dto.ReviewPhotoUploadUrlResponse;
import com.moamap.place.entity.Place;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.repository.PlaceRepository;
import org.springframework.stereotype.Service;

/**
 * 장소 리뷰 이미지 업로드 presigned URL 발급. 이미지는 1장만 허용한다.
 * 발급 권한은 리뷰 작성 권한과 동일하다(PlaceReviewService.checkMapMember와 동일 규칙).
 */
@Service
public class PlaceReviewPhotoService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long EXPIRES_IN_SECONDS = 300L;
    private static final String KEY_PREFIX = "place-reviews/";

    private final PlaceRepository placeRepository;
    private final MapClient mapClient;
    private final ObjectStoragePresigner objectStoragePresigner;

    public PlaceReviewPhotoService(PlaceRepository placeRepository, MapClient mapClient,
            ObjectStoragePresigner objectStoragePresigner) {
        this.placeRepository = placeRepository;
        this.mapClient = mapClient;
        this.objectStoragePresigner = objectStoragePresigner;
    }

    public ReviewPhotoUploadUrlResponse issueUploadUrl(Long placeId, ReviewPhotoUploadUrlRequest request, Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        Place place = getPlaceOrThrow(placeId);
        checkMapMember(place.getMapId(), userId);
        checkContentType(request.contentType());
        checkFileSize(request.fileSize());

        PresignedUploadUrl presigned = objectStoragePresigner.presign(
            KEY_PREFIX + placeId, request.contentType(), EXPIRES_IN_SECONDS);
        return new ReviewPhotoUploadUrlResponse(
            presigned.uploadUrl(), presigned.objectKey(), presigned.fileUrl(), presigned.expiresInSeconds());
    }

    private Place getPlaceOrThrow(Long placeId) {
        return placeRepository.findByIdAndDeletedAtIsNull(placeId)
            .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));
    }

    private void checkMapMember(Long mapId, Long userId) {
        if (mapClient.getMemberInfo(mapId, userId).role() == MapMemberRole.NONE) {
            throw new BusinessException(PlaceErrorCode.NOT_MAP_MEMBER);
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
