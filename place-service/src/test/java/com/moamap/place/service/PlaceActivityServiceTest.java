package com.moamap.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import com.moamap.common.exception.BusinessException;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.PlaceActivityResponse;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import com.moamap.place.repository.PlaceActivityRepository;
import com.moamap.place.user.UserClient;
import com.moamap.place.user.dto.UserProfileResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 청사진 3-1 표 A(지도 유형 x 요청자 역할 9개 조합)와, OFFICIAL 판정이 멤버십 판정보다
 * 먼저인지, PRIVATE/COMMUNITY의 리뷰 포함 여부 분기를 검증한다.
 * UNION 쿼리 자체의 정확성은 PlaceActivityRepositoryTest(@DataJpaTest)가 담당하므로
 * 여기서는 PlaceActivityRepository를 스텁으로 두고 서비스의 권한/분기 로직만 본다.
 *
 * 델타(06_architect_blueprint_nickname.md): UserClient가 새로 주입된다. 표 A 관련 테스트는
 * 전부 content가 비어 있어 userClient를 스텁하지 않아도 Mockito 기본 응답(빈 Map)으로 통과한다.
 */
@ExtendWith(MockitoExtension.class)
class PlaceActivityServiceTest {

    @Mock
    private PlaceActivityRepository placeActivityRepository;

    @Mock
    private MapClient mapClient;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private PlaceActivityService placeActivityService;

    private static final Long MAP_ID = 10L;
    private static final Long USER_ID = 1L;
    // 프로덕션 PlaceActivityService.UNKNOWN_ACTOR와 동일한 값(09_architect_blueprint_nickname.md 9장).
    private static final String UNKNOWN_ACTOR = "알 수 없음";

    /**
     * 표 A: 지도 유형 x 요청자 역할 9개 조합 전부.
     * expectedErrorCode가 null이면 200을 기대하고, includeReviews 기대값도 함께 검증한다.
     */
    private static Stream<Arguments> tableACases() {
        return Stream.of(
            // OFFICIAL은 멤버십과 무관하게 항상 차단 (실제로는 role이 항상 NONE)
            Arguments.of(MapType.OFFICIAL, MapMemberRole.NONE, PlaceErrorCode.OFFICIAL_MAP_NO_ACTIVITY_LOG, null),
            Arguments.of(MapType.PRIVATE, MapMemberRole.OWNER, null, true),
            Arguments.of(MapType.PRIVATE, MapMemberRole.ADMIN, null, true),
            Arguments.of(MapType.PRIVATE, MapMemberRole.MEMBER, null, true),
            Arguments.of(MapType.PRIVATE, MapMemberRole.NONE, PlaceErrorCode.NOT_MAP_MEMBER, null),
            Arguments.of(MapType.COMMUNITY, MapMemberRole.OWNER, null, false),
            Arguments.of(MapType.COMMUNITY, MapMemberRole.ADMIN, null, false),
            Arguments.of(MapType.COMMUNITY, MapMemberRole.MEMBER, null, false),
            // 10장 권한 정책 변경(사용자 확정): 커뮤니티 지도는 비멤버도 200 + includeReviews=false.
            Arguments.of(MapType.COMMUNITY, MapMemberRole.NONE, null, false)
        );
    }

    @ParameterizedTest(name = "{0} 지도 + {1} 역할 -> 에러:{2} / includeReviews:{3}")
    @MethodSource("tableACases")
    void 표A_지도유형과_역할_9개_조합을_그대로_따른다(
        MapType mapType, MapMemberRole role, PlaceErrorCode expectedErrorCode, Boolean expectedIncludeReviews
    ) {
        // given
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(mapType, role));
        Pageable pageable = PageRequest.of(0, 20);

        if (expectedErrorCode != null) {
            // when & then
            assertThatThrownBy(() -> placeActivityService.findByMapId(MAP_ID, USER_ID, pageable))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expectedErrorCode);
            verify(placeActivityRepository, never()).findActivities(any(), anyBoolean(), any());
            return;
        }

        given(placeActivityRepository.findActivities(eq(MAP_ID), eq(expectedIncludeReviews), eq(pageable)))
            .willReturn(new PageImpl<>(List.<Object[]>of(), pageable, 0));

        // when
        PageResponse<PlaceActivityResponse> result = placeActivityService.findByMapId(MAP_ID, USER_ID, pageable);

        // then
        assertThat(result).isNotNull();
        verify(placeActivityRepository).findActivities(MAP_ID, expectedIncludeReviews, pageable);
    }

    @Test
    void OFFICIAL_지도는_role이_OWNER여도_OFFICIAL_MAP_NO_ACTIVITY_LOG로_차단한다() {
        // given: OFFICIAL 판정이 멤버십 판정보다 먼저라는 것을, role이 NONE이 아닌 경우로도 확인
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.OFFICIAL, MapMemberRole.OWNER));
        Pageable pageable = PageRequest.of(0, 20);

        // when & then
        assertThatThrownBy(() -> placeActivityService.findByMapId(MAP_ID, USER_ID, pageable))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.OFFICIAL_MAP_NO_ACTIVITY_LOG);
        verify(placeActivityRepository, never()).findActivities(any(), anyBoolean(), any());
    }

    @Test
    void PRIVATE_지도_비멤버는_쿼리를_돌리기_전에_거부된다() {
        // 10장 권한 정책 변경으로 COMMUNITY 비멤버는 더 이상 거부되지 않으므로(표A 케이스로 이관),
        // "비멤버는 쿼리 전에 거부된다"는 이 테스트의 본래 의도는 여전히 멤버십 검증이 남아있는
        // PRIVATE 지도로 옮겨 보존한다.
        // given
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.NONE));
        Pageable pageable = PageRequest.of(0, 20);

        // when & then
        assertThatThrownBy(() -> placeActivityService.findByMapId(MAP_ID, USER_ID, pageable))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_MAP_MEMBER);
        verify(placeActivityRepository, never()).findActivities(any(), anyBoolean(), any());
    }

    @Test
    void COMMUNITY_지도는_비멤버여도_리포지토리가_includeReviews_false로_호출된다() {
        // 10장 권한 정책 변경의 핵심 보안 속성: 커뮤니티 지도를 비멤버에게 공개하더라도
        // 리뷰/별점(includeReviews=true)까지 함께 새어나가면 안 된다. 표 A 파라미터라이즈드
        // 테스트는 값 매칭(eq)으로만 확인하므로, 여기서는 ArgumentCaptor로 실제 전달값을
        // 직접 캡처해 이 불변조건을 별도로 고정한다.
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.NONE));
        Pageable pageable = PageRequest.of(0, 20);
        given(placeActivityRepository.findActivities(eq(MAP_ID), anyBoolean(), eq(pageable)))
            .willReturn(new PageImpl<>(List.<Object[]>of(), pageable, 0));
        ArgumentCaptor<Boolean> includeReviewsCaptor = ArgumentCaptor.forClass(Boolean.class);

        // when
        PageResponse<PlaceActivityResponse> result = placeActivityService.findByMapId(MAP_ID, USER_ID, pageable);

        // then
        assertThat(result).isNotNull();
        verify(placeActivityRepository).findActivities(eq(MAP_ID), includeReviewsCaptor.capture(), eq(pageable));
        assertThat(includeReviewsCaptor.getValue()).isFalse();
    }

    @Test
    void 리포지토리가_돌려준_페이지를_PageResponse로_그대로_감싼다() {
        // given
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.OWNER));
        Pageable pageable = PageRequest.of(0, 20);
        Object[] row = {"PLACE_ADDED", java.time.LocalDateTime.of(2026, 7, 27, 9, 0), 1L, 31L, "연남동 카페", null, null};
        given(placeActivityRepository.findActivities(MAP_ID, true, pageable))
            .willReturn(new PageImpl<>(List.<Object[]>of(row), pageable, 1));

        // when
        PageResponse<PlaceActivityResponse> result = placeActivityService.findByMapId(MAP_ID, USER_ID, pageable);

        // then
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).placeId()).isEqualTo(31L);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    /*
     * 회귀 방지: 셀프 리뷰에서 지적된 권한 검증 fail-open을 고정한다.
     *
     * MapMemberResponse(mapType, role)는 map-service 응답 계약을 손으로 베낀 복사본이고
     * (CLAUDE.md가 명시한 "알려진 경계면 리스크"), Jackson 역직렬화 시
     * FAIL_ON_UNKNOWN_PROPERTIES=false라 map-service가 필드명을 바꾸거나 값을 못 채우면
     * 컴파일·기존 표 A 테스트는 그대로 그린인 채로 mapType/role이 null로 들어온다.
     * 이전 구현(deny-list: "NONE이면 거부")은 null을 걸러내지 못해 그 경우 통과시켜버렸다
     * (fail-open). 지금은 allow-list(PRIVATE/COMMUNITY, OWNER/ADMIN/MEMBER만 통과)라 null도
     * 자동으로 거부되는데, 이 테스트가 없으면 누군가 allow-list를 다시 deny-list로 되돌려도
     * 표 A 9개 조합 테스트는 여전히 전부 그린이라 회귀를 잡지 못한다.
     */

    @Test
    void role이_null이면_판정_불가로_간주해_NOT_MAP_MEMBER로_거부한다() {
        // given: map-service 응답의 role 필드명이 바뀌어 역직렬화가 null을 채운 상황을 흉내
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, null));
        Pageable pageable = PageRequest.of(0, 20);

        // when & then: role이 NONE이 아니라고 해서(=null) 통과시키면 프라이빗 지도 로그가
        // 권한 판정 불가능한 요청에게 그대로 새어나간다.
        assertThatThrownBy(() -> placeActivityService.findByMapId(MAP_ID, USER_ID, pageable))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_MAP_MEMBER);
        verify(placeActivityRepository, never()).findActivities(any(), anyBoolean(), any());
    }

    @Test
    void mapType이_null이면_판정_불가로_간주해_NOT_MAP_MEMBER로_거부한다() {
        // given: mapType 필드명이 바뀌어 역직렬화가 null을 채운 상황을 흉내
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(null, MapMemberRole.OWNER));
        Pageable pageable = PageRequest.of(0, 20);

        // when & then: mapType이 OFFICIAL이 아니라고 해서(=null) PRIVATE/COMMUNITY처럼
        // 취급하면 안 된다. mapType을 모르는 채로는 리뷰 포함 여부(includeReviews)조차
        // 결정할 수 없어 더 위험하다.
        assertThatThrownBy(() -> placeActivityService.findByMapId(MAP_ID, USER_ID, pageable))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_MAP_MEMBER);
        verify(placeActivityRepository, never()).findActivities(any(), anyBoolean(), any());
    }

    @Test
    void mapType과_role이_둘_다_null이면_NOT_MAP_MEMBER로_거부한다() {
        // given: map-service 응답 자체가 텅 빈 채로 역직렬화된 최악의 상황
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(null, null));
        Pageable pageable = PageRequest.of(0, 20);

        // when & then
        assertThatThrownBy(() -> placeActivityService.findByMapId(MAP_ID, USER_ID, pageable))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_MAP_MEMBER);
        verify(placeActivityRepository, never()).findActivities(any(), anyBoolean(), any());
    }

    @Test
    void mapId와_userId를_그대로_mapClient에_전달한다() {
        // given
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
        Pageable pageable = PageRequest.of(0, 20);
        given(placeActivityRepository.findActivities(any(), anyBoolean(), any()))
            .willReturn(new PageImpl<>(List.<Object[]>of(), pageable, 0));
        ArgumentCaptor<Long> mapIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);

        // when
        placeActivityService.findByMapId(MAP_ID, USER_ID, pageable);

        // then
        verify(mapClient).getMemberInfo(mapIdCaptor.capture(), userIdCaptor.capture());
        assertThat(mapIdCaptor.getValue()).isEqualTo(MAP_ID);
        assertThat(userIdCaptor.getValue()).isEqualTo(USER_ID);
    }

    /*
     * 델타: 06_architect_blueprint_nickname.md 3-4장 불변조건 11~16.
     * "표시용 정보 실패 = 축소" 원칙(표 E)이 실제로 지켜지는지, N+1을 막는 벌크 호출 규칙이
     * 지켜지는지를 검증한다. UserClient 자체의 장애 격리(5xx/타임아웃 -> 빈 Map)는
     * UserClientTest가 담당하므로, 여기서는 PlaceActivityService가 그 결과를 어떻게 쓰는지만 본다.
     */

    private Object[] row(String eventType, LocalDateTime occurredAt, Long actorId, Long placeId, String placeName) {
        return new Object[] {eventType, occurredAt, actorId, placeId, placeName, null, null};
    }

    @Test
    void 불변조건11_같은_actorId가_여러_행에_나와도_UserClient_호출은_1회다() {
        // given: 같은 사용자가 같은 지도에서 여러 번 활동한 페이지
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.OWNER));
        Pageable pageable = PageRequest.of(0, 20);
        List<Object[]> rows = List.of(
            row("PLACE_ADDED", LocalDateTime.of(2026, 7, 27, 9, 0), 7L, 31L, "연남동 카페"),
            row("PLACE_DELETED", LocalDateTime.of(2026, 7, 27, 8, 0), 7L, 28L, "폐업한 국밥집"),
            row("REVIEW_CREATED", LocalDateTime.of(2026, 7, 27, 7, 0), 7L, 31L, "연남동 카페")
        );
        given(placeActivityRepository.findActivities(MAP_ID, true, pageable))
            .willReturn(new PageImpl<>(rows, pageable, rows.size()));
        given(userClient.findProfiles(anyCollection()))
            .willReturn(Map.of(7L, new UserProfileResponse(7L, "민서", "http://img/7")));

        // when
        placeActivityService.findByMapId(MAP_ID, USER_ID, pageable);

        // then: actorId=7L이 3번 나와도 호출은 정확히 1회
        verify(userClient, times(1)).findProfiles(anyCollection());
    }

    @Test
    void 불변조건12_UserClient에_전달되는_id는_중복없고_null없다() {
        // given
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.OWNER));
        Pageable pageable = PageRequest.of(0, 20);
        List<Object[]> rows = List.of(
            row("PLACE_ADDED", LocalDateTime.of(2026, 7, 27, 9, 0), 1L, 31L, "연남동 카페"),
            row("PLACE_ADDED", LocalDateTime.of(2026, 7, 26, 9, 0), 2L, 32L, "이태원 술집"),
            row("PLACE_ADDED", LocalDateTime.of(2026, 7, 25, 9, 0), 1L, 33L, "홍대 라멘집"),
            row("PLACE_DELETED", LocalDateTime.of(2026, 7, 24, 9, 0), null, 34L, "과거 삭제된 장소") // deleted_by 없던 시절
        );
        given(placeActivityRepository.findActivities(MAP_ID, true, pageable))
            .willReturn(new PageImpl<>(rows, pageable, rows.size()));
        given(userClient.findProfiles(anyCollection())).willReturn(Map.of());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> idsCaptor = ArgumentCaptor.forClass(Collection.class);

        // when
        placeActivityService.findByMapId(MAP_ID, USER_ID, pageable);

        // then: {1, 2}만 남아야 한다 - 중복(1) 제거, null 제외
        verify(userClient).findProfiles(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(idsCaptor.getValue()).doesNotContainNull();
        assertThat(idsCaptor.getValue()).hasSize(2);
    }

    @Test
    void 불변조건13_행위자가_없으면_UserClient를_호출하지_않는다() {
        // given: content가 비어 있는 경우
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.OWNER));
        Pageable pageable = PageRequest.of(0, 20);
        given(placeActivityRepository.findActivities(MAP_ID, true, pageable))
            .willReturn(new PageImpl<>(List.<Object[]>of(), pageable, 0));

        // when
        placeActivityService.findByMapId(MAP_ID, USER_ID, pageable);

        // then
        verify(userClient, never()).findProfiles(any());
    }

    @Test
    void 불변조건13_행이_있어도_actorId가_전부_null이면_UserClient는_호출하지_않고_닉네임은_알수없음으로_채워진다() {
        // given: 전부 deleted_by가 없던 시절의 과거 삭제 데이터인 경우
        // 회귀 방지(06_architect_blueprint_nickname.md 9장): actorId가 전부 null이라 UserClient
        // 호출을 건너뛰는 최적화 경로에서, 폴백("알 수 없음") 채우기까지 함께 건너뛰던 버그가 있었다.
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.OWNER));
        Pageable pageable = PageRequest.of(0, 20);
        List<Object[]> rows = List.<Object[]>of(
            row("PLACE_DELETED", LocalDateTime.of(2026, 7, 27, 9, 0), null, 31L, "폐업한 카페")
        );
        given(placeActivityRepository.findActivities(MAP_ID, true, pageable))
            .willReturn(new PageImpl<>(rows, pageable, rows.size()));

        // when
        PageResponse<PlaceActivityResponse> result = placeActivityService.findByMapId(MAP_ID, USER_ID, pageable);

        // then: UserClient는 호출되지 않지만(개인정보 조회 대상 자체가 없으므로), actorNickname은
        // 여전히 "알 수 없음"으로 채워져야 한다(non-null 보장). actorProfileImageUrl은 그대로 null.
        verify(userClient, never()).findProfiles(any());
        assertThat(result.content().get(0).actorId()).isNull();
        assertThat(result.content().get(0).actorNickname()).isEqualTo(UNKNOWN_ACTOR);
        assertThat(result.content().get(0).actorProfileImageUrl()).isNull();
    }

    @Test
    void 불변조건14_UserClient가_예외를_던져도_조회는_200으로_성공하고_닉네임은_알수없음으로_채워진다() {
        // given: UserClient 자체는 예외를 던지지 않는 게 원칙(표 E)이지만, 방어적으로
        // PlaceActivityService도 이 경로에서 예외가 전체 요청을 죽이지 않아야 한다.
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.OWNER));
        Pageable pageable = PageRequest.of(0, 20);
        List<Object[]> rows = List.<Object[]>of(
            row("PLACE_ADDED", LocalDateTime.of(2026, 7, 27, 9, 0), 7L, 31L, "연남동 카페")
        );
        given(placeActivityRepository.findActivities(MAP_ID, true, pageable))
            .willReturn(new PageImpl<>(rows, pageable, rows.size()));
        given(userClient.findProfiles(anyCollection())).willThrow(new RuntimeException("user-service 장애"));

        // when
        PageResponse<PlaceActivityResponse> result = placeActivityService.findByMapId(MAP_ID, USER_ID, pageable);

        // then: 예외가 전파되지 않고 200 성격의 정상 결과. actorId·placeId 등 나머지 필드는
        // 정상 응답과 완전히 동일하고, actorNickname만 "알 수 없음"으로 채워진다(non-null 보장).
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).actorId()).isEqualTo(7L);
        assertThat(result.content().get(0).placeId()).isEqualTo(31L);
        assertThat(result.content().get(0).actorNickname()).isEqualTo(UNKNOWN_ACTOR);
        assertThat(result.content().get(0).actorProfileImageUrl()).isNull();
    }

    @Test
    void 불변조건15_MapClient가_실패하면_UserClient는_호출되지_않는다() {
        // given: 권한 판정 실패는 개인정보(닉네임) 조회로 이어지면 안 된다
        given(mapClient.getMemberInfo(MAP_ID, USER_ID))
            .willThrow(new BusinessException(PlaceErrorCode.MAP_NOT_FOUND));
        Pageable pageable = PageRequest.of(0, 20);

        // when & then
        assertThatThrownBy(() -> placeActivityService.findByMapId(MAP_ID, USER_ID, pageable))
            .isInstanceOf(BusinessException.class);
        verify(userClient, never()).findProfiles(any());
        verify(placeActivityRepository, never()).findActivities(any(), anyBoolean(), any());
    }

    @Test
    void 매핑정확성_서로_다른_actorId에_각각_맞는_닉네임이_붙고_뒤섞이지_않는다() {
        // given: 1L, 2L 두 행위자가 섞인 페이지
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.OWNER));
        Pageable pageable = PageRequest.of(0, 20);
        List<Object[]> rows = List.of(
            row("PLACE_ADDED", LocalDateTime.of(2026, 7, 27, 9, 0), 1L, 31L, "연남동 카페"),
            row("PLACE_ADDED", LocalDateTime.of(2026, 7, 26, 9, 0), 2L, 32L, "이태원 술집")
        );
        given(placeActivityRepository.findActivities(MAP_ID, true, pageable))
            .willReturn(new PageImpl<>(rows, pageable, rows.size()));
        given(userClient.findProfiles(anyCollection())).willReturn(Map.of(
            1L, new UserProfileResponse(1L, "민서", "http://img/1"),
            2L, new UserProfileResponse(2L, "길동", "http://img/2")
        ));

        // when
        PageResponse<PlaceActivityResponse> result = placeActivityService.findByMapId(MAP_ID, USER_ID, pageable);

        // then: actorId별로 정확한 닉네임이 붙어야 한다 (뒤섞이면 안 됨)
        PlaceActivityResponse first = result.content().stream().filter(r -> r.actorId().equals(1L)).findFirst().orElseThrow();
        PlaceActivityResponse second = result.content().stream().filter(r -> r.actorId().equals(2L)).findFirst().orElseThrow();
        assertThat(first.actorNickname()).isEqualTo("민서");
        assertThat(first.actorProfileImageUrl()).isEqualTo("http://img/1");
        assertThat(second.actorNickname()).isEqualTo("길동");
        assertThat(second.actorProfileImageUrl()).isEqualTo("http://img/2");
    }

    @Test
    void 매핑정확성_UserClient_응답에_없는_actorId는_닉네임이_알수없음으로_채워진다() {
        // given: 표 F(개정) - 응답 배열에 없음(없는 사용자/탈퇴자) -> "알 수 없음"
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.OWNER));
        Pageable pageable = PageRequest.of(0, 20);
        List<Object[]> rows = List.<Object[]>of(
            row("PLACE_ADDED", LocalDateTime.of(2026, 7, 27, 9, 0), 999L, 31L, "연남동 카페")
        );
        given(placeActivityRepository.findActivities(MAP_ID, true, pageable))
            .willReturn(new PageImpl<>(rows, pageable, rows.size()));
        given(userClient.findProfiles(anyCollection())).willReturn(Map.of()); // 탈퇴자/없는 id라 빈 Map

        // when
        PageResponse<PlaceActivityResponse> result = placeActivityService.findByMapId(MAP_ID, USER_ID, pageable);

        // then: actorNickname은 non-null 보장, actorProfileImageUrl은 채울 URL이 없어 여전히 null
        assertThat(result.content().get(0).actorNickname()).isEqualTo(UNKNOWN_ACTOR);
        assertThat(result.content().get(0).actorProfileImageUrl()).isNull();
    }

    @Test
    void 매핑정확성_한_페이지에_성공과_실패가_섞이면_각각_실제_닉네임과_알수없음이_정확히_들어간다() {
        // given: 09장 사양 변경 - 조회 성공 행(actorId=1L), 응답 배열에 없는 행(actorId=999L),
        // actorId 자체가 null인 행(과거 삭제 데이터)이 한 페이지에 섞여 있는 경우
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.OWNER));
        Pageable pageable = PageRequest.of(0, 20);
        List<Object[]> rows = List.of(
            row("PLACE_ADDED", LocalDateTime.of(2026, 7, 27, 9, 0), 1L, 31L, "연남동 카페"),
            row("PLACE_ADDED", LocalDateTime.of(2026, 7, 26, 9, 0), 999L, 32L, "이태원 술집"),
            row("PLACE_DELETED", LocalDateTime.of(2026, 7, 25, 9, 0), null, 33L, "폐업한 카페")
        );
        given(placeActivityRepository.findActivities(MAP_ID, true, pageable))
            .willReturn(new PageImpl<>(rows, pageable, rows.size()));
        // UserClient에는 actorId != null인 {1L, 999L}만 전달되고, 999L은 응답에 없다
        given(userClient.findProfiles(anyCollection()))
            .willReturn(Map.of(1L, new UserProfileResponse(1L, "민서", "http://img/1")));

        // when
        PageResponse<PlaceActivityResponse> result = placeActivityService.findByMapId(MAP_ID, USER_ID, pageable);

        // then
        PlaceActivityResponse found = result.content().stream().filter(r -> r.placeId().equals(31L)).findFirst().orElseThrow();
        PlaceActivityResponse missingFromResponse = result.content().stream().filter(r -> r.placeId().equals(32L)).findFirst().orElseThrow();
        PlaceActivityResponse nullActorId = result.content().stream().filter(r -> r.placeId().equals(33L)).findFirst().orElseThrow();

        assertThat(found.actorNickname()).isEqualTo("민서");
        assertThat(found.actorProfileImageUrl()).isEqualTo("http://img/1");

        assertThat(missingFromResponse.actorNickname()).isEqualTo(UNKNOWN_ACTOR);
        assertThat(missingFromResponse.actorProfileImageUrl()).isNull();

        assertThat(nullActorId.actorId()).isNull();
        assertThat(nullActorId.actorNickname()).isEqualTo(UNKNOWN_ACTOR);
        assertThat(nullActorId.actorProfileImageUrl()).isNull();
    }
}
