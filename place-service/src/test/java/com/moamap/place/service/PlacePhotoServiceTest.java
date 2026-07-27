package com.moamap.place.service;

import java.util.List;
import java.util.stream.Stream;
import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.common.storage.ObjectStoragePresigner;
import com.moamap.common.storage.PresignedUploadUrl;
import com.moamap.place.dto.PhotoUploadUrlRequest;
import com.moamap.place.dto.PhotoUploadUrlRequest.FileSpec;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 장소 사진 업로드 presigned URL **배치** 발급(PlacePhotoService)의 권한 매트릭스(청사진 3-1),
 * 파일 검증 규칙(3-2), 원자적 거부(all-or-nothing)와 배치의 핵심 이득인
 * "MapClient.getMemberInfo 요청당 1회 호출"을 검증한다.
 * presigned 서명 자체는 로컬 연산이므로 ObjectStoragePresigner는 모킹하고,
 * map-service 호출은 MapClient를 모킹한다.
 *
 * 가정(구현 계약, 청사진 6장 구현순서 7): PlacePhotoService(MapClient, ObjectStoragePresigner) 생성자,
 * issueUploadUrls(PhotoUploadUrlRequest, Long userId) 메서드(복수형),
 * ObjectStoragePresigner.presign(keyPrefix="places/{mapId}", contentType, expiresInSeconds)로
 * 파일마다 위임한다고 가정한다.
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

    private FileSpec file(String contentType, long fileSize) {
        return new FileSpec(contentType, fileSize);
    }

    private PhotoUploadUrlRequest request(Long mapId, FileSpec... files) {
        return new PhotoUploadUrlRequest(mapId, List.of(files));
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
    void issueUploadUrls는_PRIVATE_COMMUNITY_지도의_모든_역할에게_발급을_허용한다(MapType mapType, MapMemberRole role) {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(mapType, role));
        stubPresign();

        // when
        List<PhotoUploadUrlResponse> responses =
            placePhotoService.issueUploadUrls(request(10L, file("image/jpeg", 1024L)), 1L);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).uploadUrl()).isEqualTo("https://upload.example.com/signed");
    }

    @Test
    void issueUploadUrls는_OFFICIAL_지도면_역할과_무관하게_BusinessException을_던진다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.OFFICIAL, MapMemberRole.OWNER));

        // when & then
        assertThatThrownBy(() -> placePhotoService.issueUploadUrls(request(10L, file("image/jpeg", 1024L)), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.OFFICIAL_MAP_NOT_REGISTRABLE);
        verifyNoInteractions(objectStoragePresigner);
    }

    @Test
    void issueUploadUrls는_role이_NONE이면_BusinessException을_던진다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.NONE));

        // when & then
        assertThatThrownBy(() -> placePhotoService.issueUploadUrls(request(10L, file("image/jpeg", 1024L)), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_MAP_MEMBER);
        verifyNoInteractions(objectStoragePresigner);
    }

    @Test
    void issueUploadUrls는_로그인하지_않았으면_mapClient를_호출하지_않고_BusinessException을_던진다() {
        // when & then
        assertThatThrownBy(() -> placePhotoService.issueUploadUrls(request(10L, file("image/jpeg", 1024L)), null))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.UNAUTHORIZED);
        verifyNoInteractions(mapClient);
        verifyNoInteractions(objectStoragePresigner);
    }

    @ParameterizedTest(name = "contentType={0} → INVALID_FILE_TYPE")
    @ValueSource(strings = {"image/gif", "application/pdf", "text/plain"})
    void issueUploadUrls는_허용되지_않은_contentType이_있으면_BusinessException을_던진다(String invalidContentType) {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));

        // when & then
        assertThatThrownBy(() -> placePhotoService.issueUploadUrls(request(10L, file(invalidContentType, 1024L)), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.INVALID_FILE_TYPE);
        verifyNoInteractions(objectStoragePresigner);
    }

    @Test
    void issueUploadUrls는_contentType_대소문자와_무관하게_화이트리스트를_통과한다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        stubPresign();

        // when
        List<PhotoUploadUrlResponse> responses =
            placePhotoService.issueUploadUrls(request(10L, file("IMAGE/JPEG", 1024L)), 1L);

        // then
        assertThat(responses).hasSize(1);
    }

    @Test
    void issueUploadUrls는_fileSize가_0이하인_파일이_있으면_BusinessException을_던진다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));

        // when & then
        assertThatThrownBy(() -> placePhotoService.issueUploadUrls(request(10L, file("image/jpeg", 0L)), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.FILE_SIZE_EXCEEDED);
        verifyNoInteractions(objectStoragePresigner);
    }

    @Test
    void issueUploadUrls는_fileSize가_5MB를_초과하는_파일이_있으면_BusinessException을_던진다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));

        // when & then
        assertThatThrownBy(() -> placePhotoService.issueUploadUrls(request(10L, file("image/jpeg", MAX_FILE_SIZE + 1)), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.FILE_SIZE_EXCEEDED);
        verifyNoInteractions(objectStoragePresigner);
    }

    @Test
    void issueUploadUrls는_fileSize가_정확히_5MB이면_허용한다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        stubPresign();

        // when
        List<PhotoUploadUrlResponse> responses =
            placePhotoService.issueUploadUrls(request(10L, file("image/jpeg", MAX_FILE_SIZE)), 1L);

        // then
        assertThat(responses).hasSize(1);
    }

    @Test
    void issueUploadUrls는_발급에_성공하면_uploadUrl_objectKey_fileUrl_expiresInSeconds를_그대로_응답에_담는다() {
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
        List<PhotoUploadUrlResponse> responses =
            placePhotoService.issueUploadUrls(request(10L, file("image/png", 2048L)), 1L);

        // then
        assertThat(responses).hasSize(1);
        PhotoUploadUrlResponse response = responses.get(0);
        assertThat(response.uploadUrl()).isEqualTo("https://upload.example.com/signed");
        assertThat(response.objectKey()).isEqualTo("places/10/uuid.png");
        assertThat(response.fileUrl()).isEqualTo("https://cdn.moamap.com/places/10/uuid.png");
        assertThat(response.expiresInSeconds()).isEqualTo(300L);
    }

    @Test
    void issueUploadUrls는_map_service가_지도를_찾지_못하면_MAP_NOT_FOUND를_그대로_전파한다() {
        // given: MapClient 기존 동작(404 → MAP_NOT_FOUND) 그대로 전파되는지 확인
        given(mapClient.getMemberInfo(10L, 1L)).willThrow(new BusinessException(PlaceErrorCode.MAP_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> placePhotoService.issueUploadUrls(request(10L, file("image/jpeg", 1024L)), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.MAP_NOT_FOUND);
        verifyNoInteractions(objectStoragePresigner);
    }

    @Test
    void issueUploadUrls는_map_service와_통신할_수_없으면_MAP_SERVICE_UNAVAILABLE을_그대로_전파한다() {
        // given: MapClient 기존 동작(통신 실패 → MAP_SERVICE_UNAVAILABLE) 그대로 전파되는지 확인
        given(mapClient.getMemberInfo(10L, 1L)).willThrow(new BusinessException(PlaceErrorCode.MAP_SERVICE_UNAVAILABLE));

        // when & then
        assertThatThrownBy(() -> placePhotoService.issueUploadUrls(request(10L, file("image/jpeg", 1024L)), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.MAP_SERVICE_UNAVAILABLE);
        verifyNoInteractions(objectStoragePresigner);
    }

    // ── 배치 전환 핵심 검증: getMemberInfo 요청당 1회 호출 (청사진 3-1, 3-6) ──────────

    @Test
    void issueUploadUrls는_파일이_5개여도_getMemberInfo를_정확히_1번만_호출한다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        stubPresign();

        // when
        List<PhotoUploadUrlResponse> responses = placePhotoService.issueUploadUrls(request(10L,
            file("image/jpeg", 1024L),
            file("image/png", 2048L),
            file("image/webp", 3072L),
            file("image/jpeg", 4096L),
            file("image/png", 5120L)
        ), 1L);

        // then
        assertThat(responses).hasSize(5);
        verify(mapClient, times(1)).getMemberInfo(10L, 1L);
    }

    // ── 원자적 거부(all-or-nothing) 검증 (청사진 3-2, 3-3, 3-6) ──────────────────

    @Test
    void issueUploadUrls는_3개_중_2번째_파일이_위반이면_발급을_0건도_하지_않고_전체_400을_던진다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));

        // when & then: 1번째는 정상이지만 2번째가 위반 → 1번째도 발급되지 않아야 한다(원자성)
        assertThatThrownBy(() -> placePhotoService.issueUploadUrls(request(10L,
            file("image/jpeg", 1024L),
            file("application/pdf", 1024L),
            file("image/png", 2048L)
        ), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.INVALID_FILE_TYPE);
        verifyNoInteractions(objectStoragePresigner);
    }

    @Test
    void issueUploadUrls는_fileSize_위반이_섞여있어도_발급을_0건도_하지_않고_전체_400을_던진다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));

        // when & then
        assertThatThrownBy(() -> placePhotoService.issueUploadUrls(request(10L,
            file("image/jpeg", 1024L),
            file("image/png", MAX_FILE_SIZE + 1),
            file("image/webp", 2048L)
        ), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.FILE_SIZE_EXCEEDED);
        verify(objectStoragePresigner, never()).presign(anyString(), anyString(), anyLong());
    }

    // ── 정상 케이스: 1~5개 응답 개수/순서 (청사진 3-6) ───────────────────────────

    @Test
    void issueUploadUrls는_files_1개면_응답도_1개다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        stubPresign();

        // when
        List<PhotoUploadUrlResponse> responses =
            placePhotoService.issueUploadUrls(request(10L, file("image/jpeg", 1024L)), 1L);

        // then
        assertThat(responses).hasSize(1);
    }

    @Test
    void issueUploadUrls는_files_5개면_응답도_5개이고_요청_순서와_같은_순서로_대응한다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        given(objectStoragePresigner.presign(eq("places/10"), eq("image/jpeg"), anyLong()))
            .willReturn(new PresignedUploadUrl("url-0", "key-0", "file-0", 300L));
        given(objectStoragePresigner.presign(eq("places/10"), eq("image/png"), anyLong()))
            .willReturn(new PresignedUploadUrl("url-1", "key-1", "file-1", 300L));
        given(objectStoragePresigner.presign(eq("places/10"), eq("image/webp"), anyLong()))
            .willReturn(new PresignedUploadUrl("url-2", "key-2", "file-2", 300L));

        // when
        List<PhotoUploadUrlResponse> responses = placePhotoService.issueUploadUrls(request(10L,
            file("image/jpeg", 1024L),
            file("image/png", 2048L),
            file("image/webp", 3072L),
            file("image/jpeg", 4096L),
            file("image/png", 5120L)
        ), 1L);

        // then: i번째 응답 == i번째 요청 파일 (인덱스 매칭)
        assertThat(responses).hasSize(5);
        assertThat(responses.get(0).uploadUrl()).isEqualTo("url-0");
        assertThat(responses.get(1).uploadUrl()).isEqualTo("url-1");
        assertThat(responses.get(2).uploadUrl()).isEqualTo("url-2");
        assertThat(responses.get(3).uploadUrl()).isEqualTo("url-0");
        assertThat(responses.get(4).uploadUrl()).isEqualTo("url-1");
    }
}
