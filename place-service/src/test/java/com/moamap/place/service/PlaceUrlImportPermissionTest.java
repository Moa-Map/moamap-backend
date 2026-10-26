package com.moamap.place.service;

import java.math.BigDecimal;
import java.util.List;
import com.moamap.common.exception.BusinessException;
import com.moamap.place.dto.PlaceBulkCreateRequest;
import com.moamap.place.dto.PlaceCreateRequest;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.event.PlaceCountEventPublisher;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import com.moamap.place.repository.PlaceLikeRepository;
import com.moamap.place.repository.PlaceRepository;
import com.moamap.place.user.UserBlockClient;
import com.moamap.place.user.UserClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 지도의 'URL로 장소 추가 허용' 설정이 장소 등록에 적용되는지 검증한다.
 * 설정은 일반 멤버에게만 적용되고, 방장·관리자와 직접 검색 등록은 영향을 받지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class PlaceUrlImportPermissionTest {

    private static final Long MAP_ID = 10L;
    private static final Long USER_ID = 1L;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceLikeRepository placeLikeRepository;

    @Mock
    private MapClient mapClient;

    @Mock
    private UserClient userClient;

    @Mock
    private UserBlockClient userBlockClient;

    @Mock
    private PlaceBulkRegistrar placeBulkRegistrar;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PlaceCountEventPublisher placeCountEventPublisher;

    @InjectMocks
    private PlaceService placeService;

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PlaceSourceType.class, names = "KAKAO_SEARCH", mode = EnumSource.Mode.EXCLUDE)
    void 설정이_꺼진_지도의_일반_멤버는_URL로_가져온_장소를_등록할_수_없다(PlaceSourceType source) {
        givenMember(MapType.COMMUNITY, MapMemberRole.MEMBER, false);

        assertThatThrownBy(() -> placeService.create(createRequest(source), USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.URL_IMPORT_NOT_ALLOWED);
        verify(placeRepository, never()).saveAndFlush(any());
    }

    @Test
    void 프라이빗_지도도_설정이_꺼져_있으면_일반_멤버의_URL_등록을_막는다() {
        givenMember(MapType.PRIVATE, MapMemberRole.MEMBER, false);

        assertThatThrownBy(() -> placeService.create(createRequest(PlaceSourceType.INSTAGRAM), USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.URL_IMPORT_NOT_ALLOWED);
    }

    @Test
    void 설정이_켜진_지도의_일반_멤버는_URL로_가져온_장소를_등록할_수_있다() {
        givenMember(MapType.COMMUNITY, MapMemberRole.MEMBER, true);
        givenSaveSucceeds();

        assertThat(placeService.create(createRequest(PlaceSourceType.INSTAGRAM), USER_ID)).isNotNull();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = MapMemberRole.class, names = {"OWNER", "ADMIN"})
    void 방장과_관리자는_설정이_꺼져_있어도_URL로_가져온_장소를_등록할_수_있다(MapMemberRole role) {
        givenMember(MapType.COMMUNITY, role, false);
        givenSaveSucceeds();

        assertThat(placeService.create(createRequest(PlaceSourceType.NAVER_MAP), USER_ID)).isNotNull();
    }

    @Test
    void 설정이_꺼져_있어도_직접_검색한_장소는_일반_멤버가_등록할_수_있다() {
        givenMember(MapType.COMMUNITY, MapMemberRole.MEMBER, false);
        givenSaveSucceeds();

        assertThat(placeService.create(createRequest(PlaceSourceType.KAKAO_SEARCH), USER_ID)).isNotNull();
    }

    @Test
    void 일괄_등록은_URL로_가져온_장소가_하나라도_있으면_한_건도_등록하지_않는다() {
        givenMember(MapType.COMMUNITY, MapMemberRole.MEMBER, false);
        PlaceBulkCreateRequest request = new PlaceBulkCreateRequest(MAP_ID, List.of(
            bulkItem("직접 검색한 곳", "111", PlaceSourceType.KAKAO_SEARCH),
            bulkItem("공유 링크로 가져온 곳", "222", PlaceSourceType.KAKAO_MAP)));

        assertThatThrownBy(() -> placeService.createBulk(request, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.URL_IMPORT_NOT_ALLOWED);
        verifyNoInteractions(placeBulkRegistrar);
    }

    private void givenMember(MapType mapType, MapMemberRole role, boolean allowUrlImport) {
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(mapType, role, allowUrlImport));
    }

    private void givenSaveSucceeds() {
        given(placeRepository.saveAndFlush(any(Place.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    private PlaceCreateRequest createRequest(PlaceSourceType source) {
        return new PlaceCreateRequest("스타벅스 강남점", "서울 강남구 테헤란로 1", "서울 강남구 테헤란로 1",
            BigDecimal.valueOf(37.497852), BigDecimal.valueOf(127.027618), "카페", "26338954",
            source, sourceUrlOf(source), null, MAP_ID, null, null);
    }

    private PlaceBulkCreateRequest.Item bulkItem(String name, String kakaoPlaceId, PlaceSourceType source) {
        return new PlaceBulkCreateRequest.Item(name, "주소", "도로명주소",
            BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0), "카페",
            kakaoPlaceId, source, sourceUrlOf(source), null, null, null);
    }

    private String sourceUrlOf(PlaceSourceType source) {
        return source.isFromUrl() ? "https://example.com/shared" : null;
    }
}
