package com.moamap.place.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import com.moamap.common.exception.BusinessException;
import com.moamap.place.dto.PlaceCreateRequest;
import com.moamap.place.dto.PlaceResponse;
import com.moamap.place.dto.PlaceUpdateRequest;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.entity.PlaceStatus;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import com.moamap.place.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private MapClient mapClient;

    @InjectMocks
    private PlaceService placeService;

    private PlaceCreateRequest createRequest() {
        return new PlaceCreateRequest(
            "스타벅스 강남점",
            "서울 강남구 테헤란로 1",
            "서울 강남구 테헤란로 1",
            BigDecimal.valueOf(37.497852),
            BigDecimal.valueOf(127.027618),
            "카페",
            "26338954",
            PlaceSourceType.KAKAO_SEARCH,
            null,
            null,
            10L
        );
    }

    @Test
    void create는_PRIVATE_지도면_role과_무관하게_APPROVED로_저장한다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        given(placeRepository.save(any(Place.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        PlaceResponse response = placeService.create(createRequest(), 1L);

        // then
        assertThat(response.status()).isEqualTo(PlaceStatus.APPROVED);
    }

    @Test
    void create는_COMMUNITY_지도에서_OWNER면_APPROVED로_저장한다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.OWNER));
        given(placeRepository.save(any(Place.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        PlaceResponse response = placeService.create(createRequest(), 1L);

        // then
        assertThat(response.status()).isEqualTo(PlaceStatus.APPROVED);
    }

    @Test
    void create는_COMMUNITY_지도에서_ADMIN이면_APPROVED로_저장한다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.ADMIN));
        given(placeRepository.save(any(Place.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        PlaceResponse response = placeService.create(createRequest(), 1L);

        // then
        assertThat(response.status()).isEqualTo(PlaceStatus.APPROVED);
    }

    @Test
    void create는_COMMUNITY_지도에서_MEMBER면_PENDING으로_저장한다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));
        given(placeRepository.save(any(Place.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        PlaceResponse response = placeService.create(createRequest(), 1L);

        // then
        assertThat(response.status()).isEqualTo(PlaceStatus.PENDING);
    }

    @Test
    void create는_role이_NONE이면_BusinessException을_던진다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.NONE));

        // when & then
        assertThatThrownBy(() -> placeService.create(createRequest(), 1L))
            .isInstanceOf(BusinessException.class);
        verify(placeRepository, never()).save(any());
    }

    @Test
    void create는_OFFICIAL_지도면_role과_무관하게_BusinessException을_던진다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.OFFICIAL, MapMemberRole.OWNER));

        // when & then
        assertThatThrownBy(() -> placeService.create(createRequest(), 1L))
            .isInstanceOf(BusinessException.class);
        verify(placeRepository, never()).save(any());
    }

    @Test
    void create는_로그인하지_않았으면_mapService를_호출하지_않고_BusinessException을_던진다() {
        // when & then
        assertThatThrownBy(() -> placeService.create(createRequest(), null))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(mapClient);
        verify(placeRepository, never()).save(any());
    }

    @Test
    void findById는_존재하지_않으면_BusinessException을_던진다() {
        // given
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> placeService.findById(1L))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void findById는_존재하면_정상적으로_반환한다() {
        // given
        Place place = Place.builder()
            .name("스타벅스 강남점")
            .mapId(10L)
            .createdBy(1L)
            .build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));

        // when
        PlaceResponse response = placeService.findById(1L);

        // then
        assertThat(response.name()).isEqualTo("스타벅스 강남점");
    }

    @Test
    void findAllByMapId는_해당_지도의_장소_목록을_반환한다() {
        // given
        given(placeRepository.findByMapIdAndDeletedAtIsNull(10L)).willReturn(List.of(
            Place.builder().name("스타벅스 강남점").mapId(10L).createdBy(1L).build()
        ));

        // when
        List<PlaceResponse> result = placeService.findAllByMapId(10L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).mapId()).isEqualTo(10L);
    }

    @Test
    void update는_생성자가_요청하면_필드를_변경한다() {
        // given
        Place place = Place.builder()
            .name("old")
            .mapId(10L)
            .createdBy(1L)
            .build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        PlaceUpdateRequest request = new PlaceUpdateRequest("new", "new address", "new road address",
            BigDecimal.ONE, BigDecimal.TEN, "카페", "new description");

        // when
        PlaceResponse response = placeService.update(1L, 1L, request);

        // then
        assertThat(response.name()).isEqualTo("new");
        assertThat(response.description()).isEqualTo("new description");
    }

    @Test
    void update는_생성자가_아니면_BusinessException을_던진다() {
        // given
        Place place = Place.builder()
            .name("old")
            .mapId(10L)
            .createdBy(1L)
            .build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        PlaceUpdateRequest request = new PlaceUpdateRequest("new", "new address", "new road address",
            BigDecimal.ONE, BigDecimal.TEN, "카페", "new description");

        // when & then
        assertThatThrownBy(() -> placeService.update(1L, 2L, request))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void update는_로그인하지_않았으면_BusinessException을_던진다() {
        // given
        Place place = Place.builder()
            .name("old")
            .mapId(10L)
            .createdBy(1L)
            .build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        PlaceUpdateRequest request = new PlaceUpdateRequest("new", "new address", "new road address",
            BigDecimal.ONE, BigDecimal.TEN, "카페", "new description");

        // when & then
        assertThatThrownBy(() -> placeService.update(1L, null, request))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void delete는_생성자가_요청하면_소프트_삭제한다() {
        // given
        Place place = Place.builder()
            .name("삭제될 장소")
            .mapId(10L)
            .createdBy(1L)
            .build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));

        // when
        placeService.delete(1L, 1L);

        // then
        assertThat(place.getDeletedAt()).isNotNull();
        verify(placeRepository, never()).delete(any());
    }

    @Test
    void delete는_생성자가_아니면_BusinessException을_던진다() {
        // given
        Place place = Place.builder()
            .name("삭제될 장소")
            .mapId(10L)
            .createdBy(1L)
            .build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));

        // when & then
        assertThatThrownBy(() -> placeService.delete(1L, 2L))
            .isInstanceOf(BusinessException.class);
        assertThat(place.getDeletedAt()).isNull();
    }
}
