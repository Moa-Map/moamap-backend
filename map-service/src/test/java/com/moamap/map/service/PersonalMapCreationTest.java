package com.moamap.map.service;

import java.util.List;
import com.moamap.common.exception.BusinessException;
import com.moamap.map.config.JpaAuditingConfig;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;
import com.moamap.map.exception.MapErrorCode;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapMemberRepository;
import com.moamap.map.user.UserClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 나만의 지도 생성 규칙을 실제 DB(H2)에 저장해가며 검증한다.
 * 이벤트 연동 없이도 이 로직만 따로 확인할 수 있어야, 나중에 문제가 생겼을 때 원인을 가릴 수 있다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, MapService.class, InviteCodeGenerator.class})
class PersonalMapCreationTest {

    private static final long USER_ID = 1L;
    private static final String MAP_NAME = "나만의 지도";

    @Autowired
    private MapService mapService;

    @Autowired
    private MapEntityRepository mapRepository;

    @Autowired
    private MapMemberRepository mapMemberRepository;

    // 나만의 지도 생성과는 무관하지만 MapService가 주입받는 협력자라 컨텍스트에 채워준다.
    @MockitoBean
    private UserClient userClient;

    @Test
    void 나만의_지도를_만들면_소유자가_OWNER로_참여한다() {
        mapService.createPersonalMapIfAbsent(USER_ID, MAP_NAME);

        MapEntity map = personalMapOf(USER_ID);
        assertThat(map.getName()).isEqualTo(MAP_NAME);
        assertThat(map.isPersonal()).isTrue();
        assertThat(map.getOwnerId()).isEqualTo(USER_ID);
        assertThat(mapMemberRepository.findByMapIdAndUserId(map.getId(), USER_ID))
            .get()
            .extracting(MapMember::getRole)
            .isEqualTo(MapRole.OWNER);
    }

    @Test
    void 나만의_지도는_PRIVATE_타입으로_만들어진다() {
        mapService.createPersonalMapIfAbsent(USER_ID, MAP_NAME);

        // MapType에 값을 더하면 이 enum을 미러링하는 place-service가 깨지므로 PRIVATE을 그대로 쓴다.
        assertThat(personalMapOf(USER_ID).getType()).isEqualTo(MapType.PRIVATE);
    }

    @Test
    void 나만의_지도에는_초대_코드를_발급하지_않는다() {
        mapService.createPersonalMapIfAbsent(USER_ID, MAP_NAME);

        // 코드가 없으니 초대로 합류할 수 없다 — 혼자 쓰는 지도라는 성격이 여기서 보장된다.
        assertThat(personalMapOf(USER_ID).getInviteCode()).isNull();
    }

    @Test
    void 이미_있으면_다시_만들지_않는다() {
        mapService.createPersonalMapIfAbsent(USER_ID, MAP_NAME);
        mapService.createPersonalMapIfAbsent(USER_ID, MAP_NAME);
        mapService.createPersonalMapIfAbsent(USER_ID, MAP_NAME);

        // 메시지가 여러 번 배달돼도 지도는 하나여야 한다.
        assertThat(personalMapsOf(USER_ID)).hasSize(1);
    }

    @Test
    void 사용자마다_각자의_나만의_지도를_갖는다() {
        mapService.createPersonalMapIfAbsent(USER_ID, MAP_NAME);
        mapService.createPersonalMapIfAbsent(2L, MAP_NAME);

        assertThat(personalMapsOf(USER_ID)).hasSize(1);
        assertThat(personalMapsOf(2L)).hasSize(1);
    }

    @Test
    void 나만의_지도는_삭제할_수_없다() {
        mapService.createPersonalMapIfAbsent(USER_ID, MAP_NAME);
        Long mapId = personalMapOf(USER_ID).getId();

        assertThatThrownBy(() -> mapService.delete(mapId, USER_ID))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.CANNOT_DELETE_PERSONAL_MAP);

        assertThat(mapRepository.findById(mapId)).isPresent();
    }

    @Test
    void 일반_지도는_기존대로_삭제된다() {
        MapEntity normal = mapRepository.save(
            MapEntity.create("친구들과 쓰는 지도", null, null, MapType.PRIVATE, USER_ID, List.of(), "CODE12345678"));
        mapMemberRepository.save(MapMember.of(normal.getId(), USER_ID, MapRole.OWNER));

        mapService.delete(normal.getId(), USER_ID);

        assertThat(mapRepository.findById(normal.getId())).isEmpty();
    }

    private MapEntity personalMapOf(Long userId) {
        return personalMapsOf(userId).get(0);
    }

    private List<MapEntity> personalMapsOf(Long userId) {
        return mapRepository.findAll().stream()
            .filter(map -> map.isPersonal() && map.getOwnerId().equals(userId))
            .toList();
    }
}
