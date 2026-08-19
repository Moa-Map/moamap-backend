package com.moamap.map.service;

import java.util.List;
import java.util.Map;
import com.moamap.common.exception.BusinessException;
import com.moamap.map.config.JpaAuditingConfig;
import com.moamap.map.dto.MapMemberListResponse;
import com.moamap.map.dto.MapMemberSummaryResponse;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;
import com.moamap.map.exception.MapErrorCode;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapMemberRepository;
import com.moamap.map.place.PlaceClient;
import com.moamap.map.user.UserClient;
import com.moamap.map.user.dto.UserProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

/**
 * 멤버 목록 조회의 권한·정렬·프로필 결합을 검증한다.
 * user-service 호출은 대역으로 바꿔, 그쪽이 실패해도 목록이 나가는지까지 확인한다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, MapService.class, InviteCodeGenerator.class})
class MapMemberListTest {

    private static final long OWNER_ID = 1L;
    private static final long ADMIN_ID = 2L;
    private static final long MEMBER_ID = 3L;
    private static final long OUTSIDER_ID = 99L;

    @Autowired
    private MapService mapService;

    @Autowired
    private MapEntityRepository mapRepository;

    @Autowired
    private MapMemberRepository mapMemberRepository;

    @MockitoBean
    private UserClient userClient;

    @MockitoBean
    private PlaceClient placeClient;

    private Long mapId;

    @BeforeEach
    void setUp() {
        MapEntity map = mapRepository.save(
            MapEntity.create("여행 지도", null, null, MapType.COMMUNITY, OWNER_ID, List.of(), null));
        mapId = map.getId();
        // 일부러 역할 순서를 섞어 저장한다 — 정렬이 저장 순서가 아니라 역할로 이뤄지는지 보기 위함이다.
        mapMemberRepository.save(MapMember.of(mapId, MEMBER_ID, MapRole.MEMBER));
        mapMemberRepository.save(MapMember.of(mapId, OWNER_ID, MapRole.OWNER));
        mapMemberRepository.save(MapMember.of(mapId, ADMIN_ID, MapRole.ADMIN));
    }

    @Test
    void 멤버를_방장_관리자_멤버_순으로_돌려준다() {
        givenProfiles(Map.of());

        MapMemberListResponse response = mapService.getMembers(mapId, OWNER_ID);

        assertThat(response.members())
            .extracting(MapMemberSummaryResponse::role)
            .containsExactly(MapRole.OWNER, MapRole.ADMIN, MapRole.MEMBER);
    }

    @Test
    void 참여자_수를_함께_돌려준다() {
        givenProfiles(Map.of());

        assertThat(mapService.getMembers(mapId, OWNER_ID).memberCount()).isEqualTo(3);
    }

    @Test
    void 닉네임과_프로필_이미지를_채워서_돌려준다() {
        givenProfiles(Map.of(
            OWNER_ID, new UserProfileResponse(OWNER_ID, "방장님", "https://img/owner.jpg"),
            ADMIN_ID, new UserProfileResponse(ADMIN_ID, "관리자님", null)));

        MapMemberListResponse response = mapService.getMembers(mapId, OWNER_ID);

        assertThat(response.members().get(0).nickname()).isEqualTo("방장님");
        assertThat(response.members().get(0).profileImageUrl()).isEqualTo("https://img/owner.jpg");
        assertThat(response.members().get(1).nickname()).isEqualTo("관리자님");
    }

    @Test
    void 프로필_조회에_실패해도_멤버와_역할은_그대로_돌려준다() {
        // user-service 장애가 지도 화면 장애로 번지면 안 된다.
        givenProfiles(Map.of());

        MapMemberListResponse response = mapService.getMembers(mapId, OWNER_ID);

        assertThat(response.members()).hasSize(3);
        assertThat(response.members()).allSatisfy(member -> {
            assertThat(member.userId()).isNotNull();
            assertThat(member.role()).isNotNull();
            assertThat(member.nickname()).isNull();
        });
    }

    @Test
    void 일부_프로필만_받아도_나머지는_빈_채로_돌려준다() {
        givenProfiles(Map.of(OWNER_ID, new UserProfileResponse(OWNER_ID, "방장님", null)));

        MapMemberListResponse response = mapService.getMembers(mapId, OWNER_ID);

        assertThat(response.members().get(0).nickname()).isEqualTo("방장님");
        assertThat(response.members().get(1).nickname()).isNull();
    }

    @Test
    void 멤버별_등록_장소_수를_채워서_돌려준다() {
        givenProfiles(Map.of());
        givenPlaceCounts(Map.of(OWNER_ID, 12L, ADMIN_ID, 0L, MEMBER_ID, 5L));

        MapMemberListResponse response = mapService.getMembers(mapId, OWNER_ID);

        assertThat(response.members())
            .extracting(MapMemberSummaryResponse::userId, MapMemberSummaryResponse::placeCount)
            .containsExactly(tuple(OWNER_ID, 12L), tuple(ADMIN_ID, 0L), tuple(MEMBER_ID, 5L));
    }

    /*
     * 장소 수를 못 받아온 것과 "0개 등록"은 다르다. 0으로 채우면 화면에 틀린 숫자가 그대로 보이므로,
     * 닉네임과 같은 규칙으로 null을 남겨 모른다는 사실을 그대로 전달한다.
     */

    @Test
    void 장소_수_조회에_실패해도_멤버_목록은_그대로_돌려준다() {
        givenProfiles(Map.of());
        givenPlaceCounts(Map.of());

        MapMemberListResponse response = mapService.getMembers(mapId, OWNER_ID);

        assertThat(response.members()).hasSize(3);
        assertThat(response.members()).allSatisfy(member -> {
            assertThat(member.role()).isNotNull();
            assertThat(member.placeCount()).isNull();
        });
    }

    @Test
    void 멤버라면_방장이_아니어도_조회할_수_있다() {
        givenProfiles(Map.of());

        assertThat(mapService.getMembers(mapId, MEMBER_ID).members()).hasSize(3);
    }

    @Test
    void 참여하지_않은_사용자는_조회할_수_없다() {
        assertThatThrownBy(() -> mapService.getMembers(mapId, OUTSIDER_ID))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.NOT_MAP_MEMBER);
    }

    @Test
    void 없는_지도를_조회하면_지도를_찾을_수_없다고_알린다() {
        assertThatThrownBy(() -> mapService.getMembers(999L, OWNER_ID))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.MAP_NOT_FOUND);
    }

    private void givenProfiles(Map<Long, UserProfileResponse> profiles) {
        given(userClient.findProfiles(anyCollection())).willReturn(profiles);
    }

    private void givenPlaceCounts(Map<Long, Long> counts) {
        given(placeClient.countByCreator(anyLong(), anyCollection())).willReturn(counts);
    }
}
