package com.moamap.place.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.hibernate.exception.ConstraintViolationException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
            10L,
            null
        );
    }

    private static Stream<Arguments> createStatusCases() {
        return Stream.of(
            Arguments.of(MapType.PRIVATE, MapMemberRole.MEMBER, PlaceStatus.APPROVED),
            Arguments.of(MapType.COMMUNITY, MapMemberRole.OWNER, PlaceStatus.APPROVED),
            Arguments.of(MapType.COMMUNITY, MapMemberRole.ADMIN, PlaceStatus.APPROVED),
            Arguments.of(MapType.COMMUNITY, MapMemberRole.MEMBER, PlaceStatus.PENDING)
        );
    }

    @ParameterizedTest(name = "{0} 지도 + {1} 역할 → {2}")
    @MethodSource("createStatusCases")
    void create는_지도유형과_역할에_따라_초기_상태를_결정한다(MapType mapType, MapMemberRole role, PlaceStatus expectedStatus) {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(mapType, role));
        given(placeRepository.saveAndFlush(any(Place.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        PlaceResponse response = placeService.create(createRequest(), 1L);

        // then
        assertThat(response.status()).isEqualTo(expectedStatus);
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
        verify(placeRepository, never()).saveAndFlush(any());
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
        verify(placeRepository, never()).saveAndFlush(any());
    }

    @Test
    void create는_로그인하지_않았으면_mapService를_호출하지_않고_BusinessException을_던진다() {
        // when & then
        assertThatThrownBy(() -> placeService.create(createRequest(), null))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.UNAUTHORIZED);
        verifyNoInteractions(mapClient);
        verify(placeRepository, never()).saveAndFlush(any());
    }

    @Test
    void create는_같은_지도에_동일한_kakaoPlaceId가_있으면_BusinessException을_던진다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        given(placeRepository.existsByMapIdAndKakaoPlaceIdAndDeletedAtIsNull(10L, "26338954")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> placeService.create(createRequest(), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.DUPLICATE_PLACE);
        verify(placeRepository, never()).saveAndFlush(any());
    }

    @Test
    void create는_사전체크를_통과해도_DB_유니크_제약_위반이면_DUPLICATE_PLACE_예외로_변환한다() {
        // given: 동시 요청 race condition으로 checkDuplicate는 통과했지만 실제 insert 시점엔 이미 다른 트랜잭션이 커밋한 상황을 재현
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        given(placeRepository.existsByMapIdAndKakaoPlaceIdAndDeletedAtIsNull(10L, "26338954")).willReturn(false);
        ConstraintViolationException cause = new ConstraintViolationException(
            "duplicate key value violates unique constraint", null, "uk_places_map_kakao_place");
        given(placeRepository.saveAndFlush(any(Place.class)))
            .willThrow(new DataIntegrityViolationException("uk_places_map_kakao_place violation", cause));

        // when & then
        assertThatThrownBy(() -> placeService.create(createRequest(), 1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.DUPLICATE_PLACE);
    }

    @Test
    void create는_다른_무결성_제약_위반이면_DUPLICATE_PLACE로_바꾸지_않고_그대로_전파한다() {
        // given: 유니크 제약이 아닌 다른 제약(예: 길이 초과) 위반은 중복으로 오인하면 안 된다
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        given(placeRepository.existsByMapIdAndKakaoPlaceIdAndDeletedAtIsNull(10L, "26338954")).willReturn(false);
        ConstraintViolationException cause = new ConstraintViolationException(
            "value too long for type character varying(100)", null, "places_name_check");
        DataIntegrityViolationException thrown = new DataIntegrityViolationException("length violation", cause);
        given(placeRepository.saveAndFlush(any(Place.class))).willThrow(thrown);

        // when & then
        assertThatThrownBy(() -> placeService.create(createRequest(), 1L))
            .isSameAs(thrown);
    }

    @Test
    void create는_요청에_담긴_태그를_그대로_저장한다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        given(placeRepository.saveAndFlush(any(Place.class))).willAnswer(invocation -> invocation.getArgument(0));
        PlaceCreateRequest request = new PlaceCreateRequest(
            "스타벅스 강남점", "서울 강남구 테헤란로 1", "서울 강남구 테헤란로 1",
            BigDecimal.valueOf(37.497852), BigDecimal.valueOf(127.027618), "카페", "26338954",
            PlaceSourceType.KAKAO_SEARCH, null, null, 10L, List.of("데이트", "조용한")
        );

        // when
        PlaceResponse response = placeService.create(request, 1L);

        // then
        assertThat(response.tags()).containsExactly("데이트", "조용한");
    }

    @Test
    void create는_태그가_없으면_빈_리스트로_저장한다() {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        given(placeRepository.saveAndFlush(any(Place.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        PlaceResponse response = placeService.create(createRequest(), 1L);

        // then
        assertThat(response.tags()).isEmpty();
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

    /*
     * update와 delete는 둘 다 checkModifyPermission(place, userId)을 그대로 공유한다.
     * 그 권한 분기(로그인/멤버여부/PRIVATE/COMMUNITY 방장·관리자/본인 소유)는 여기 update 쪽에서
     * 전체 매트릭스로 검증하고, delete 쪽은 고유 동작(소프트 삭제)과 대표 거부 케이스만 남긴다.
     */

    @Test
    void update는_COMMUNITY_지도에서_생성자_본인이면_수정된다() {
        // given
        Place place = Place.builder().name("old").mapId(10L).createdBy(1L).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));
        PlaceUpdateRequest request = new PlaceUpdateRequest("new", "new address", "new road address",
            BigDecimal.ONE, BigDecimal.TEN, "카페", "new description", null);

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
            .tags(new ArrayList<>(List.of("기존태그")))
            .build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));
        PlaceUpdateRequest request = new PlaceUpdateRequest("new name", null, null, null, null, null, null, null);

        // when
        PlaceResponse response = placeService.update(1L, 1L, request);

        // then
        assertThat(response.name()).isEqualTo("new name");
        assertThat(response.address()).isEqualTo("old address");
        assertThat(response.category()).isEqualTo("카페");
        assertThat(response.tags()).containsExactly("기존태그");
    }

    @Test
    void update는_태그를_보내면_기존_태그를_통째로_교체한다() {
        // given
        Place place = Place.builder()
            .name("old")
            .mapId(10L)
            .createdBy(1L)
            .tags(new ArrayList<>(List.of("old1", "old2")))
            .build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));
        PlaceUpdateRequest request = new PlaceUpdateRequest(null, null, null, null, null, null, null, List.of("new1"));

        // when
        PlaceResponse response = placeService.update(1L, 1L, request);

        // then
        assertThat(response.tags()).containsExactly("new1");
    }

    @Test
    void update는_COMMUNITY_지도에서_방장이면_생성자가_아니어도_수정된다() {
        // given
        Place place = Place.builder().name("old").mapId(10L).createdBy(1L).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.OWNER));
        PlaceUpdateRequest request = new PlaceUpdateRequest("new", "new address", "new road address",
            BigDecimal.ONE, BigDecimal.TEN, "카페", "new description", null);

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
            BigDecimal.ONE, BigDecimal.TEN, "카페", "new description", null);

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
            BigDecimal.ONE, BigDecimal.TEN, "카페", "new description", null);

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
            BigDecimal.ONE, BigDecimal.TEN, "카페", "new description", null);

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
            BigDecimal.ONE, BigDecimal.TEN, "카페", "new description", null);

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
        Place place = Place.builder().name("삭제될 장소").mapId(10L).createdBy(1L).kakaoPlaceId("26338954").build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));

        // when
        placeService.delete(1L, 1L);

        // then
        assertThat(place.getDeletedAt()).isNotNull();
        // uk_places_map_kakao_place는 deleted_at을 구분하지 않으므로, kakaoPlaceId를 비워야 같은 지도에 재등록할 수 있다.
        assertThat(place.getKakaoPlaceId()).isNull();
        verify(placeRepository, never()).delete(any());
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
        assertThat(response.processedBy()).isEqualTo(2L);
        assertThat(response.processedAt()).isNotNull();
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

    /*
     * reject는 approve와 requireReviewer/checkPending 가드를 그대로 공유한다.
     * 그 가드 분기는 approve 쪽에서 이미 검증했으니, 여기서는 reject 고유 동작(REJECTED 전환)만 확인한다.
     */
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
        assertThat(response.processedBy()).isEqualTo(2L);
    }
}
