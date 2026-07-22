package com.moamap.place.service;

import java.util.stream.Stream;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
 * 장소 사진 업로드 presigned URL 발급(PlacePhotoService)의 권한 매트릭스(청사진 3-1)와
 * 파일 검증 규칙(3-2)을 전 케이스로 검증한다. presigned 서명 자체는 로컬 연산이므로
 * ObjectStoragePresigner는 모킹하고, map-service 호출은 MapClient를 모킹한다.
 *
 * 가정(구현 계약): PlacePhotoService(MapClient, ObjectStoragePresigner) 생성자,
 * issueUploadUrl(PhotoUploadUrlRequest, Long userId) 메서드,
 * ObjectStoragePresigner.presign(keyPrefix="places/{mapId}", contentType, expiresInSeconds)로 위임한다고 가정한다.
 */
@ExtendWith(MockitoExtension.class)
class PlacePhotoServiceTest {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    @Mock
    private MapClient mapClient;

    @Mock
    private ObjectStoragePresigner objectStoragePresigner;

    @InjectMocks
    private PlacePhotoService placePhotoService;

    private PhotoUploadUrlRequest request(Long mapId, String contentType, long fileSize) {
        return new PhotoUploadUrlRequest(mapId, contentType, fileSize);
    }

    private void stubPresign() {
        given(objectStoragePresigner.presign(anyString(), anyString(), anyLong()))
            .willReturn(new PresignedUploadUrl(
                "https://upload.example.com/signed",
                "places/10/uuid.jpg",
                "https://cdn.moamap.com/places/10/uuid.jpg",
                300L
            ));
    }

    private static Stream<Arguments> issuableCases() {
        return Stream.of(
            Arguments.of(MapType.PRIVATE, MapMemberRole.OWNER),
            Arguments.of(MapType.PRIVATE, MapMemberRole.ADMIN),
            Arguments.of(MapType.PRIVATE, MapMemberRole.MEMBER),
            Arguments.of(MapType.COMMUNITY, MapMemberRole.OWNER),
            Arguments.of(MapType.COMMUNITY, MapMemberRole.ADMIN),
            Arguments.of(MapType.COMMUNITY, MapMemberRole.MEMBER)
        );
    }

    @ParameterizedTest(name = "{0} 지도 + {1} 역할 → 발급 허용")
    @MethodSource("issuableCases")
    void issueUploadUrl은_PRIVATE_COMMUNITY_지도의_모든_역할에게_발급을_허용한다(MapType mapType, MapMemberRole role) {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(mapType, role));
        stubPresign();

        // when
        PhotoUploadUrlResponse response = placePhotoService.issueUploadUrl(request(10L, "image/jpeg", 1024L), 1L);

        // then
        assertThat(response.uploadUrl()).isEqualTo("https://upload.example.com/signed");
    }

    @Test
    void issueUploadUrl은_OFFICIAL_지도면_역할과_무관하게_BusinessException을_던진다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.OFFICIAL, MapMemberRole.OWNER));

        // when & then
        assertThatThrownBy(() -> placePhotoService.issueUploadUrl(request(10L, "image/jpeg", 1024L), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.OFFICIAL_MAP_NOT_REGISTRABLE);
        verifyNoInteractions(objectStoragePresigner);
    }

    @Test
    void issueUploadUrl은_role이_NONE이면_BusinessException을_던진다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.NONE));

        // when & then
        assertThatThrownBy(() -> placePhotoService.issueUploadUrl(request(10L, "image/jpeg", 1024L), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_MAP_MEMBER);
        verifyNoInteractions(objectStoragePresigner);
    }

    @Test
    void issueUploadUrl은_로그인하지_않았으면_mapClient를_호출하지_않고_BusinessException을_던진다() {
        // when & then
        assertThatThrownBy(() -> placePhotoService.issueUploadUrl(request(10L, "image/jpeg", 1024L), null))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.UNAUTHORIZED);
        verifyNoInteractions(mapClient);
        verifyNoInteractions(objectStoragePresigner);
    }

    @ParameterizedTest(name = "contentType={0} → INVALID_FILE_TYPE")
    @ValueSource(strings = {"image/gif", "application/pdf", "text/plain"})
    void issueUploadUrl은_허용되지_않은_contentType이면_BusinessException을_던진다(String invalidContentType) {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));

        // when & then
        assertThatThrownBy(() -> placePhotoService.issueUploadUrl(request(10L, invalidContentType, 1024L), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.INVALID_FILE_TYPE);
        verifyNoInteractions(objectStoragePresigner);
    }

    @Test
    void issueUploadUrl은_contentType_대소문자와_무관하게_화이트리스트를_통과한다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        stubPresign();

        // when
        PhotoUploadUrlResponse response = placePhotoService.issueUploadUrl(request(10L, "IMAGE/JPEG", 1024L), 1L);

        // then
        assertThat(response).isNotNull();
    }

    @Test
    void issueUploadUrl은_fileSize가_0이하면_BusinessException을_던진다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));

        // when & then
        assertThatThrownBy(() -> placePhotoService.issueUploadUrl(request(10L, "image/jpeg", 0L), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.FILE_SIZE_EXCEEDED);
        verifyNoInteractions(objectStoragePresigner);
    }

    @Test
    void issueUploadUrl은_fileSize가_5MB를_초과하면_BusinessException을_던진다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));

        // when & then
        assertThatThrownBy(() -> placePhotoService.issueUploadUrl(request(10L, "image/jpeg", MAX_FILE_SIZE + 1), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.FILE_SIZE_EXCEEDED);
        verifyNoInteractions(objectStoragePresigner);
    }

    @Test
    void issueUploadUrl은_fileSize가_정확히_5MB이면_허용한다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        stubPresign();

        // when
        PhotoUploadUrlResponse response = placePhotoService.issueUploadUrl(request(10L, "image/jpeg", MAX_FILE_SIZE), 1L);

        // then
        assertThat(response).isNotNull();
    }

    @Test
    void issueUploadUrl은_발급에_성공하면_uploadUrl_objectKey_fileUrl_expiresInSeconds를_그대로_응답에_담는다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        given(objectStoragePresigner.presign(eq("places/10"), eq("image/png"), anyLong()))
            .willReturn(new PresignedUploadUrl(
                "https://upload.example.com/signed",
                "places/10/uuid.png",
                "https://cdn.moamap.com/places/10/uuid.png",
                300L
            ));

        // when
        PhotoUploadUrlResponse response = placePhotoService.issueUploadUrl(request(10L, "image/png", 2048L), 1L);

        // then
        assertThat(response.uploadUrl()).isEqualTo("https://upload.example.com/signed");
        assertThat(response.objectKey()).isEqualTo("places/10/uuid.png");
        assertThat(response.fileUrl()).isEqualTo("https://cdn.moamap.com/places/10/uuid.png");
        assertThat(response.expiresInSeconds()).isEqualTo(300L);
    }

    @Test
    void issueUploadUrl은_map_service가_지도를_찾지_못하면_MAP_NOT_FOUND를_그대로_전파한다() {
        // given: MapClient 기존 동작(404 → MAP_NOT_FOUND) 그대로 전파되는지 확인
        given(mapClient.getMemberInfo(10L, 1L)).willThrow(new BusinessException(PlaceErrorCode.MAP_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> placePhotoService.issueUploadUrl(request(10L, "image/jpeg", 1024L), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.MAP_NOT_FOUND);
        verifyNoInteractions(objectStoragePresigner);
    }

    @Test
    void issueUploadUrl은_map_service와_통신할_수_없으면_MAP_SERVICE_UNAVAILABLE을_그대로_전파한다() {
        // given: MapClient 기존 동작(통신 실패 → MAP_SERVICE_UNAVAILABLE) 그대로 전파되는지 확인
        given(mapClient.getMemberInfo(10L, 1L)).willThrow(new BusinessException(PlaceErrorCode.MAP_SERVICE_UNAVAILABLE));

        // when & then
        assertThatThrownBy(() -> placePhotoService.issueUploadUrl(request(10L, "image/jpeg", 1024L), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.MAP_SERVICE_UNAVAILABLE);
        verifyNoInteractions(objectStoragePresigner);
    }
}
