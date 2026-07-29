package com.moamap.map.service;

import java.util.Optional;
import com.moamap.common.exception.BusinessException;
import com.moamap.map.dto.MapMemberRoleUpdateRequest;
import com.moamap.map.dto.MapMemberRoleUpdateResponse;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;
import com.moamap.map.exception.MapErrorCode;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 청사진 3-1(가) 결정 규칙 표를 행 단위로 검증한다.
 * 표의 "null → Bean Validation 400" 행은 컨트롤러 @Valid의 몫이라 여기서 다루지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class MapServiceTest {

    private static final Long MAP_ID = 100L;
    private static final Long OWNER_ID = 1L;
    private static final Long TARGET_ID = 2L;

    @Mock
    private MapEntityRepository mapRepository;

    @Mock
    private MapMemberRepository mapMemberRepository;

    @Mock
    private InviteCodeGenerator inviteCodeGenerator;

    @InjectMocks
    private MapService mapService;

    @ParameterizedTest
    @EnumSource(value = MapRole.class, names = {"OWNER", "NONE"})
    void 요청_role이_OWNER나_NONE이면_INVALID_ROLE_ASSIGNMENT를_던진다(MapRole invalidRole) {
        assertThatThrownBy(() -> mapService.changeMemberRole(
            MAP_ID, OWNER_ID, TARGET_ID, new MapMemberRoleUpdateRequest(invalidRole)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.INVALID_ROLE_ASSIGNMENT);

        verify(mapRepository, never()).findById(anyLong());
    }

    @Test
    void 지도가_없으면_MAP_NOT_FOUND를_던진다() {
        given(mapRepository.findById(MAP_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> mapService.changeMemberRole(
            MAP_ID, OWNER_ID, TARGET_ID, new MapMemberRoleUpdateRequest(MapRole.ADMIN)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.MAP_NOT_FOUND);
    }

    @Test
    void requester가_지도_소유자가_아니면_NOT_MAP_OWNER를_던진다() {
        MapEntity map = communityMap(OWNER_ID);
        given(mapRepository.findById(MAP_ID)).willReturn(Optional.of(map));

        Long notOwner = 999L;

        assertThatThrownBy(() -> mapService.changeMemberRole(
            MAP_ID, notOwner, TARGET_ID, new MapMemberRoleUpdateRequest(MapRole.ADMIN)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.NOT_MAP_OWNER);

        verify(mapMemberRepository, never()).findByMapIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void target이_지도_멤버가_아니면_TARGET_NOT_MAP_MEMBER를_던진다() {
        MapEntity map = communityMap(OWNER_ID);
        given(mapRepository.findById(MAP_ID)).willReturn(Optional.of(map));
        given(mapMemberRepository.findByMapIdAndUserId(MAP_ID, TARGET_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> mapService.changeMemberRole(
            MAP_ID, OWNER_ID, TARGET_ID, new MapMemberRoleUpdateRequest(MapRole.ADMIN)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.TARGET_NOT_MAP_MEMBER);
    }

    @Test
    void target이_소유자_본인이면_CANNOT_CHANGE_OWNER_ROLE을_던진다() {
        MapEntity map = communityMap(OWNER_ID);
        given(mapRepository.findById(MAP_ID)).willReturn(Optional.of(map));
        given(mapMemberRepository.findByMapIdAndUserId(MAP_ID, OWNER_ID))
            .willReturn(Optional.of(MapMember.of(MAP_ID, OWNER_ID, MapRole.OWNER)));

        assertThatThrownBy(() -> mapService.changeMemberRole(
            MAP_ID, OWNER_ID, OWNER_ID, new MapMemberRoleUpdateRequest(MapRole.MEMBER)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.CANNOT_CHANGE_OWNER_ROLE);
    }

    @Test
    void MEMBER를_ADMIN으로_승격한다() {
        MapEntity map = communityMap(OWNER_ID);
        given(mapRepository.findById(MAP_ID)).willReturn(Optional.of(map));
        MapMember target = MapMember.of(MAP_ID, TARGET_ID, MapRole.MEMBER);
        given(mapMemberRepository.findByMapIdAndUserId(MAP_ID, TARGET_ID)).willReturn(Optional.of(target));

        MapMemberRoleUpdateResponse response = mapService.changeMemberRole(
            MAP_ID, OWNER_ID, TARGET_ID, new MapMemberRoleUpdateRequest(MapRole.ADMIN));

        assertThat(response.mapId()).isEqualTo(MAP_ID);
        assertThat(response.userId()).isEqualTo(TARGET_ID);
        assertThat(response.role()).isEqualTo(MapRole.ADMIN);
        assertThat(target.getRole()).isEqualTo(MapRole.ADMIN);
    }

    @Test
    void ADMIN에_ADMIN을_다시_요청하면_변화_없이_멱등하게_성공한다() {
        MapEntity map = communityMap(OWNER_ID);
        given(mapRepository.findById(MAP_ID)).willReturn(Optional.of(map));
        MapMember target = MapMember.of(MAP_ID, TARGET_ID, MapRole.ADMIN);
        given(mapMemberRepository.findByMapIdAndUserId(MAP_ID, TARGET_ID)).willReturn(Optional.of(target));

        MapMemberRoleUpdateResponse response = mapService.changeMemberRole(
            MAP_ID, OWNER_ID, TARGET_ID, new MapMemberRoleUpdateRequest(MapRole.ADMIN));

        assertThat(response.role()).isEqualTo(MapRole.ADMIN);
        assertThat(target.getRole()).isEqualTo(MapRole.ADMIN);
    }

    @Test
    void ADMIN을_MEMBER로_강등한다() {
        MapEntity map = communityMap(OWNER_ID);
        given(mapRepository.findById(MAP_ID)).willReturn(Optional.of(map));
        MapMember target = MapMember.of(MAP_ID, TARGET_ID, MapRole.ADMIN);
        given(mapMemberRepository.findByMapIdAndUserId(MAP_ID, TARGET_ID)).willReturn(Optional.of(target));

        MapMemberRoleUpdateResponse response = mapService.changeMemberRole(
            MAP_ID, OWNER_ID, TARGET_ID, new MapMemberRoleUpdateRequest(MapRole.MEMBER));

        assertThat(response.role()).isEqualTo(MapRole.MEMBER);
        assertThat(target.getRole()).isEqualTo(MapRole.MEMBER);
    }

    private MapEntity communityMap(Long ownerId) {
        return MapEntity.create("테스트 지도", "설명", null, MapType.COMMUNITY, ownerId, null, null);
    }
}
