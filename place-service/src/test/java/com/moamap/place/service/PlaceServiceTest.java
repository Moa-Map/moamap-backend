package com.moamap.place.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.PlaceCreateRequest;
import com.moamap.place.dto.PlaceResponse;
import com.moamap.place.dto.PlaceUpdateRequest;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.entity.PlaceStatus;
import com.moamap.place.exception.PlaceErrorCode;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_MAP_MEMBER);
        verify(placeRepository, never()).save(any());
    }

    @Test
    void create는_OFFICIAL_지도면_role과_무관하게_BusinessException을_던진다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.OFFICIAL, MapMemberRole.OWNER));

        // when & then
        assertThatThrownBy(() -> placeService.create(createRequest(), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.OFFICIAL_MAP_NOT_REGISTRABLE);
        verify(placeRepository, never()).save(any());
    }

    @Test
    void create는_로그인하지_않았으면_mapService를_호출하지_않고_BusinessException을_던진다() {
        // when & then
        assertThatThrownBy(() -> placeService.create(createRequest(), null))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.UNAUTHORIZED);
        verifyNoInteractions(mapClient);
        verify(placeRepository, never()).save(any());
    }

    @Test
    void findById는_존재하지_않으면_BusinessException을_던진다() {
        // given
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> placeService.findById(1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.PLACE_NOT_FOUND);
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
    void findAllByMapId는_APPROVED_상태만_반환하고_mapClient를_호출하지_않는다() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(placeRepository.findByMapIdAndStatusAndDeletedAtIsNull(10L, PlaceStatus.APPROVED, pageable)).willReturn(
            new PageImpl<>(List.of(
                Place.builder().name("승인된 장소").mapId(10L).createdBy(1L).status(PlaceStatus.APPROVED).build()
            ), pageable, 1)
        );

        // when
        PageResponse<PlaceResponse> result = placeService.findAllByMapId(10L, pageable);

        // then
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).name()).isEqualTo("승인된 장소");
        assertThat(result.totalElements()).isEqualTo(1);
        verifyNoInteractions(mapClient);
    }

    @Test
    void findPendingByMapId는_방장_관리자가_요청하면_PENDING_목록을_반환한다() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.OWNER));
        given(placeRepository.findByMapIdAndStatusAndDeletedAtIsNull(10L, PlaceStatus.PENDING, pageable)).willReturn(
            new PageImpl<>(List.of(
                Place.builder().name("대기중인 장소").mapId(10L).createdBy(1L).status(PlaceStatus.PENDING).build()
            ), pageable, 1)
        );

        // when
        PageResponse<PlaceResponse> result = placeService.findPendingByMapId(10L, 1L, pageable);

        // then
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).name()).isEqualTo("대기중인 장소");
    }

    @Test
    void findPendingByMapId는_방장_관리자가_아니면_BusinessException을_던진다() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(mapClient.getMemberInfo(10L, 3L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));

        // when & then
        assertThatThrownBy(() -> placeService.findPendingByMapId(10L, 3L, pageable))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_REVIEWER);
        verify(placeRepository, never()).findByMapIdAndStatusAndDeletedAtIsNull(any(), any(), any());
    }

    @Test
    void findPendingByMapId는_로그인하지_않았으면_BusinessException을_던진다() {
        // when & then
        assertThatThrownBy(() -> placeService.findPendingByMapId(10L, null, PageRequest.of(0, 20)))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.UNAUTHORIZED);
        verifyNoInteractions(mapClient);
    }

    @Test
    void update는_COMMUNITY_지도에서_생성자_본인이면_수정된다() {
        // given
        Place place = Place.builder().name("old").mapId(10L).createdBy(1L).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));
        PlaceUpdateRequest request = new PlaceUpdateRequest("new", "new address", "new road address",
            BigDecimal.ONE, BigDecimal.TEN, "카페", "new description");

        // when
        PlaceResponse response = placeService.update(1L, 1L, request);

        // then
        assertThat(response.name()).isEqualTo("new");
        assertThat(response.description()).isEqualTo("new description");
    }

    @Test
    void update는_null인_필드는_기존_값을_유지한다() {
        // given
        Place place = Place.builder()
            .name("old")
            .address("old address")
            .category("카페")
            .mapId(10L)
            .createdBy(1L)
            .build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));
        PlaceUpdateRequest request = new PlaceUpdateRequest("new name", null, null, null, null, null, null);

        // when
        PlaceResponse response = placeService.update(1L, 1L, request);

        // then
        assertThat(response.name()).isEqualTo("new name");
        assertThat(response.address()).isEqualTo("old address");
        assertThat(response.category()).isEqualTo("카페");
    }

    @Test
    void update는_COMMUNITY_지도에서_방장이면_생성자가_아니어도_수정된다() {
        // given
        Place place = Place.builder().name("old").mapId(10L).createdBy(1L).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.OWNER));
        PlaceUpdateRequest request = new PlaceUpdateRequest("new", "new address", "new road address",
            BigDecimal.ONE, BigDecimal.TEN, "카페", "new description");

        // when
        PlaceResponse response = placeService.update(1L, 2L, request);

        // then
        assertThat(response.name()).isEqualTo("new");
    }

    @Test
    void update는_COMMUNITY_지도에서_관리자면_생성자가_아니어도_수정된다() {
        // given
        Place place = Place.builder().name("old").mapId(10L).createdBy(1L).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.ADMIN));
        PlaceUpdateRequest request = new PlaceUpdateRequest("new", "new address", "new road address",
            BigDecimal.ONE, BigDecimal.TEN, "카페", "new description");

        // when
        PlaceResponse response = placeService.update(1L, 2L, request);

        // then
        assertThat(response.name()).isEqualTo("new");
    }

    @Test
    void update는_COMMUNITY_지도에서_일반멤버가_본인_장소가_아니면_BusinessException을_던진다() {
        // given
        Place place = Place.builder().name("old").mapId(10L).createdBy(1L).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));
        PlaceUpdateRequest request = new PlaceUpdateRequest("new", "new address", "new road address",
            BigDecimal.ONE, BigDecimal.TEN, "카페", "new description");

        // when & then
        assertThatThrownBy(() -> placeService.update(1L, 2L, request))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_PLACE_OWNER);
    }

    @Test
    void update는_PRIVATE_지도면_생성자가_아니어도_수정된다() {
        // given
        Place place = Place.builder().name("old").mapId(10L).createdBy(1L).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        PlaceUpdateRequest request = new PlaceUpdateRequest("new", "new address", "new road address",
            BigDecimal.ONE, BigDecimal.TEN, "카페", "new description");

        // when
        PlaceResponse response = placeService.update(1L, 2L, request);

        // then
        assertThat(response.name()).isEqualTo("new");
    }

    @Test
    void update는_해당_지도의_멤버가_아니면_BusinessException을_던진다() {
        // given
        Place place = Place.builder().name("old").mapId(10L).createdBy(1L).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.NONE));
        PlaceUpdateRequest request = new PlaceUpdateRequest("new", "new address", "new road address",
            BigDecimal.ONE, BigDecimal.TEN, "카페", "new description");

        // when & then
        assertThatThrownBy(() -> placeService.update(1L, 2L, request))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_MAP_MEMBER);
    }

    @Test
    void update는_로그인하지_않았으면_BusinessException을_던진다() {
        // given
        Place place = Place.builder().name("old").mapId(10L).createdBy(1L).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        PlaceUpdateRequest request = new PlaceUpdateRequest("new", "new address", "new road address",
            BigDecimal.ONE, BigDecimal.TEN, "카페", "new description");

        // when & then
        assertThatThrownBy(() -> placeService.update(1L, null, request))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.UNAUTHORIZED);
        verifyNoInteractions(mapClient);
    }

    @Test
    void delete는_COMMUNITY_지도에서_생성자_본인이면_소프트_삭제한다() {
        // given
        Place place = Place.builder().name("삭제될 장소").mapId(10L).createdBy(1L).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));

        // when
        placeService.delete(1L, 1L);

        // then
        assertThat(place.getDeletedAt()).isNotNull();
        verify(placeRepository, never()).delete(any());
    }

    @Test
    void delete는_COMMUNITY_지도에서_방장이면_생성자가_아니어도_삭제한다() {
        // given
        Place place = Place.builder().name("삭제될 장소").mapId(10L).createdBy(1L).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.OWNER));

        // when
        placeService.delete(1L, 2L);

        // then
        assertThat(place.getDeletedAt()).isNotNull();
    }

    @Test
    void delete는_COMMUNITY_지도에서_관리자면_생성자가_아니어도_삭제한다() {
        // given
        Place place = Place.builder().name("삭제될 장소").mapId(10L).createdBy(1L).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.ADMIN));

        // when
        placeService.delete(1L, 2L);

        // then
        assertThat(place.getDeletedAt()).isNotNull();
    }

    @Test
    void delete는_COMMUNITY_지도에서_일반멤버가_본인_장소가_아니면_BusinessException을_던진다() {
        // given
        Place place = Place.builder().name("삭제될 장소").mapId(10L).createdBy(1L).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));

        // when & then
        assertThatThrownBy(() -> placeService.delete(1L, 2L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_PLACE_OWNER);
        assertThat(place.getDeletedAt()).isNull();
    }

    @Test
    void delete는_PRIVATE_지도면_생성자가_아니어도_삭제한다() {
        // given
        Place place = Place.builder().name("삭제될 장소").mapId(10L).createdBy(1L).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));

        // when
        placeService.delete(1L, 2L);

        // then
        assertThat(place.getDeletedAt()).isNotNull();
    }

    @Test
    void delete는_해당_지도의_멤버가_아니면_BusinessException을_던진다() {
        // given
        Place place = Place.builder().name("삭제될 장소").mapId(10L).createdBy(1L).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.NONE));

        // when & then
        assertThatThrownBy(() -> placeService.delete(1L, 2L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_MAP_MEMBER);
        assertThat(place.getDeletedAt()).isNull();
    }

    @Test
    void delete는_로그인하지_않았으면_BusinessException을_던진다() {
        // given
        Place place = Place.builder().name("삭제될 장소").mapId(10L).createdBy(1L).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));

        // when & then
        assertThatThrownBy(() -> placeService.delete(1L, null))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.UNAUTHORIZED);
        assertThat(place.getDeletedAt()).isNull();
        verifyNoInteractions(mapClient);
    }

    @Test
    void approve는_방장_관리자가_요청하면_APPROVED로_변경한다() {
        // given
        Place place = Place.builder()
            .name("대기중인 장소")
            .mapId(10L)
            .createdBy(1L)
            .status(PlaceStatus.PENDING)
            .build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.ADMIN));

        // when
        PlaceResponse response = placeService.approve(1L, 2L);

        // then
        assertThat(response.status()).isEqualTo(PlaceStatus.APPROVED);
        assertThat(response.reviewedBy()).isEqualTo(2L);
        assertThat(response.reviewedAt()).isNotNull();
    }

    @Test
    void approve는_방장_관리자가_아니면_BusinessException을_던진다() {
        // given
        Place place = Place.builder()
            .name("대기중인 장소")
            .mapId(10L)
            .createdBy(1L)
            .status(PlaceStatus.PENDING)
            .build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 3L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));

        // when & then
        assertThatThrownBy(() -> placeService.approve(1L, 3L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_REVIEWER);
        assertThat(place.getStatus()).isEqualTo(PlaceStatus.PENDING);
    }

    @Test
    void approve는_이미_처리된_장소면_BusinessException을_던진다() {
        // given
        Place place = Place.builder()
            .name("이미 승인된 장소")
            .mapId(10L)
            .createdBy(1L)
            .status(PlaceStatus.APPROVED)
            .build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.OWNER));

        // when & then
        assertThatThrownBy(() -> placeService.approve(1L, 2L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.ALREADY_PROCESSED);
    }

    @Test
    void reject는_방장_관리자가_요청하면_REJECTED로_변경한다() {
        // given
        Place place = Place.builder()
            .name("대기중인 장소")
            .mapId(10L)
            .createdBy(1L)
            .status(PlaceStatus.PENDING)
            .build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.OWNER));

        // when
        PlaceResponse response = placeService.reject(1L, 2L);

        // then
        assertThat(response.status()).isEqualTo(PlaceStatus.REJECTED);
        assertThat(response.reviewedBy()).isEqualTo(2L);
    }

    @Test
    void reject는_방장_관리자가_아니면_BusinessException을_던진다() {
        // given
        Place place = Place.builder()
            .name("대기중인 장소")
            .mapId(10L)
            .createdBy(1L)
            .status(PlaceStatus.PENDING)
            .build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 3L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.NONE));

        // when & then
        assertThatThrownBy(() -> placeService.reject(1L, 3L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_REVIEWER);
        assertThat(place.getStatus()).isEqualTo(PlaceStatus.PENDING);
    }

    @Test
    void reject는_이미_처리된_장소면_BusinessException을_던진다() {
        // given
        Place place = Place.builder()
            .name("이미 반려된 장소")
            .mapId(10L)
            .createdBy(1L)
            .status(PlaceStatus.REJECTED)
            .build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.OWNER));

        // when & then
        assertThatThrownBy(() -> placeService.reject(1L, 2L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.ALREADY_PROCESSED);
    }
}
