package com.moamap.place.service;

import java.math.BigDecimal;
import java.util.Optional;
import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.common.storage.ObjectStoragePresigner;
import com.moamap.common.storage.PresignedUploadUrl;
import com.moamap.place.dto.ReviewPhotoUploadUrlRequest;
import com.moamap.place.dto.ReviewPhotoUploadUrlResponse;
import com.moamap.place.entity.Place;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import com.moamap.place.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 리뷰 이미지 업로드 presigned URL 발급의 권한(리뷰 작성 권한과 동일)·파일 검증 규칙을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PlaceReviewPhotoServiceTest {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final long PLACE_ID = 10L;
    private static final long MAP_ID = 100L;
    private static final long USER_ID = 1L;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private MapClient mapClient;

    @Mock
    private ObjectStoragePresigner objectStoragePresigner;

    @InjectMocks
    private PlaceReviewPhotoService placeReviewPhotoService;

    private Place place() {
        return Place.builder().id(PLACE_ID).mapId(MAP_ID).name("장소").lat(BigDecimal.ZERO).lng(BigDecimal.ZERO).build();
    }

    private ReviewPhotoUploadUrlRequest request(String contentType, long fileSize) {
        return new ReviewPhotoUploadUrlRequest(contentType, fileSize);
    }

    private void stubPresign() {
        given(objectStoragePresigner.presign(anyString(), anyString(), anyLong()))
            .willReturn(new PresignedUploadUrl(
                "https://upload.example.com/signed",
                "place-reviews/10/uuid.jpg",
                "https://cdn.moamap.com/place-reviews/10/uuid.jpg",
                300L));
    }

    @Test
    void 발급에_성공하면_uploadUrl_objectKey_fileUrl_expiresInSeconds를_그대로_응답에_담는다() {
        given(placeRepository.findByIdAndDeletedAtIsNull(PLACE_ID)).willReturn(Optional.of(place()));
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        stubPresign();

        ReviewPhotoUploadUrlResponse response =
            placeReviewPhotoService.issueUploadUrl(PLACE_ID, request("image/jpeg", 1024L), USER_ID);

        assertThat(response.uploadUrl()).isEqualTo("https://upload.example.com/signed");
        assertThat(response.objectKey()).isEqualTo("place-reviews/10/uuid.jpg");
        assertThat(response.fileUrl()).isEqualTo("https://cdn.moamap.com/place-reviews/10/uuid.jpg");
        assertThat(response.expiresInSeconds()).isEqualTo(300L);
    }

    @Test
    void place_reviews_접두사로_placeId별로_나뉘어_발급한다() {
        given(placeRepository.findByIdAndDeletedAtIsNull(PLACE_ID)).willReturn(Optional.of(place()));
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        stubPresign();

        placeReviewPhotoService.issueUploadUrl(PLACE_ID, request("image/jpeg", 1024L), USER_ID);

        org.mockito.Mockito.verify(objectStoragePresigner).presign(eq("place-reviews/10"), eq("image/jpeg"), anyLong());
    }

    @Test
    void 존재하지_않는_장소면_PLACE_NOT_FOUND를_던진다() {
        given(placeRepository.findByIdAndDeletedAtIsNull(PLACE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> placeReviewPhotoService.issueUploadUrl(PLACE_ID, request("image/jpeg", 1024L), USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.PLACE_NOT_FOUND);
        verifyNoInteractions(mapClient, objectStoragePresigner);
    }

    @Test
    void 해당_지도의_멤버가_아니면_NOT_MAP_MEMBER를_던진다() {
        given(placeRepository.findByIdAndDeletedAtIsNull(PLACE_ID)).willReturn(Optional.of(place()));
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.NONE));

        assertThatThrownBy(() -> placeReviewPhotoService.issueUploadUrl(PLACE_ID, request("image/jpeg", 1024L), USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_MAP_MEMBER);
        verifyNoInteractions(objectStoragePresigner);
    }

    @Test
    void 로그인하지_않았으면_placeRepository를_호출하지_않고_UNAUTHORIZED를_던진다() {
        assertThatThrownBy(() -> placeReviewPhotoService.issueUploadUrl(PLACE_ID, request("image/jpeg", 1024L), null))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.UNAUTHORIZED);
        verifyNoInteractions(placeRepository, mapClient, objectStoragePresigner);
    }

    @ParameterizedTest(name = "contentType={0} → INVALID_FILE_TYPE")
    @ValueSource(strings = {"image/gif", "application/pdf", "text/plain"})
    void 허용되지_않은_형식이면_INVALID_FILE_TYPE을_던진다(String invalidContentType) {
        given(placeRepository.findByIdAndDeletedAtIsNull(PLACE_ID)).willReturn(Optional.of(place()));
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));

        assertThatThrownBy(() -> placeReviewPhotoService.issueUploadUrl(PLACE_ID, request(invalidContentType, 1024L), USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.INVALID_FILE_TYPE);
        verifyNoInteractions(objectStoragePresigner);
    }

    @Test
    void fileSize가_0이하면_FILE_SIZE_EXCEEDED를_던진다() {
        given(placeRepository.findByIdAndDeletedAtIsNull(PLACE_ID)).willReturn(Optional.of(place()));
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));

        assertThatThrownBy(() -> placeReviewPhotoService.issueUploadUrl(PLACE_ID, request("image/jpeg", 0L), USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.FILE_SIZE_EXCEEDED);
        verifyNoInteractions(objectStoragePresigner);
    }

    @Test
    void fileSize가_5MB를_초과하면_FILE_SIZE_EXCEEDED를_던진다() {
        given(placeRepository.findByIdAndDeletedAtIsNull(PLACE_ID)).willReturn(Optional.of(place()));
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));

        assertThatThrownBy(() -> placeReviewPhotoService.issueUploadUrl(PLACE_ID, request("image/jpeg", MAX_FILE_SIZE + 1), USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.FILE_SIZE_EXCEEDED);
        verifyNoInteractions(objectStoragePresigner);
    }

    @Test
    void fileSize가_정확히_5MB이면_허용한다() {
        given(placeRepository.findByIdAndDeletedAtIsNull(PLACE_ID)).willReturn(Optional.of(place()));
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        stubPresign();

        ReviewPhotoUploadUrlResponse response =
            placeReviewPhotoService.issueUploadUrl(PLACE_ID, request("image/jpeg", MAX_FILE_SIZE), USER_ID);

        assertThat(response).isNotNull();
    }

    @Test
    void map_service가_지도를_찾지_못하면_MAP_NOT_FOUND를_그대로_전파한다() {
        given(placeRepository.findByIdAndDeletedAtIsNull(PLACE_ID)).willReturn(Optional.of(place()));
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willThrow(new BusinessException(PlaceErrorCode.MAP_NOT_FOUND));

        assertThatThrownBy(() -> placeReviewPhotoService.issueUploadUrl(PLACE_ID, request("image/jpeg", 1024L), USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.MAP_NOT_FOUND);
        verifyNoInteractions(objectStoragePresigner);
    }
}
