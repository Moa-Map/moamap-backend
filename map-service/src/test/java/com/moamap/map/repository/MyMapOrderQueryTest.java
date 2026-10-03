package com.moamap.map.repository;

import java.util.List;
import com.moamap.map.config.JpaAuditingConfig;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모음 탭 목록이 사용자가 지정한 순서를 따르는지 쿼리로 검증한다.
 *
 * 정렬 기준이 조인 대상(MapMember)에 있고 NULL 처리까지 얽혀 있어, 쿼리를 실제로 실행해봐야 한다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class MyMapOrderQueryTest {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;

    @Autowired
    private MapEntityRepository mapRepository;

    @Autowired
    private MapMemberRepository mapMemberRepository;

    private MapEntity first;
    private MapEntity second;
    private MapEntity third;
    private int inviteCodeSeq;

    @BeforeEach
    void setUp() {
        // 저장 순서 = 생성 순서. 순서를 지정하지 않으면 최신순(=역순)으로 나와야 한다.
        first = mapRepository.save(privateMap("가장 먼저 만든 지도"));
        second = mapRepository.save(privateMap("두 번째 지도"));
        third = mapRepository.save(privateMap("가장 나중 지도"));

        join(first, USER_ID);
        join(second, USER_ID);
        join(third, USER_ID);
    }

    @Test
    void 순서를_지정한_적_없으면_최신순으로_내려준다() {
        Page<MapEntity> page = mapRepository.findJoinedByTypeInMyOrder(
            USER_ID, MapType.PRIVATE, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(MapEntity::getName)
            .containsExactly("가장 나중 지도", "두 번째 지도", "가장 먼저 만든 지도");
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void 지정한_순서가_최신순보다_우선한다() {
        order(first, USER_ID, 0);
        order(third, USER_ID, 1);
        order(second, USER_ID, 2);

        Page<MapEntity> page = mapRepository.findJoinedByTypeInMyOrder(
            USER_ID, MapType.PRIVATE, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(MapEntity::getName)
            .containsExactly("가장 먼저 만든 지도", "가장 나중 지도", "두 번째 지도");
    }

    @Test
    void 순서가_없는_지도는_있는_지도_뒤에_최신순으로_붙는다() {
        // 순서를 지정한 지도가 하나뿐이어도, 나머지가 앞으로 끼어들면 안 된다.
        order(first, USER_ID, 0);

        Page<MapEntity> page = mapRepository.findJoinedByTypeInMyOrder(
            USER_ID, MapType.PRIVATE, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(MapEntity::getName)
            .containsExactly("가장 먼저 만든 지도", "가장 나중 지도", "두 번째 지도");
    }

    @Test
    void 순서는_사용자별로_독립이다() {
        join(first, OTHER_USER_ID);
        join(second, OTHER_USER_ID);
        order(second, OTHER_USER_ID, 0);
        order(first, OTHER_USER_ID, 1);

        Page<MapEntity> mine = mapRepository.findJoinedByTypeInMyOrder(
            USER_ID, MapType.PRIVATE, PageRequest.of(0, 10));
        Page<MapEntity> others = mapRepository.findJoinedByTypeInMyOrder(
            OTHER_USER_ID, MapType.PRIVATE, PageRequest.of(0, 10));

        // 상대가 순서를 바꿔도 내 목록은 그대로 최신순이다.
        assertThat(mine.getContent()).extracting(MapEntity::getName)
            .containsExactly("가장 나중 지도", "두 번째 지도", "가장 먼저 만든 지도");
        assertThat(others.getContent()).extracting(MapEntity::getName)
            .containsExactly("두 번째 지도", "가장 먼저 만든 지도");
    }

    @Test
    void 페이지를_나눠도_지정한_순서를_이어서_내려준다() {
        order(first, USER_ID, 0);
        order(third, USER_ID, 1);
        order(second, USER_ID, 2);

        Page<MapEntity> firstPage = mapRepository.findJoinedByTypeInMyOrder(
            USER_ID, MapType.PRIVATE, PageRequest.of(0, 2));
        Page<MapEntity> secondPage = mapRepository.findJoinedByTypeInMyOrder(
            USER_ID, MapType.PRIVATE, PageRequest.of(1, 2));

        assertThat(firstPage.getContent()).extracting(MapEntity::getName)
            .containsExactly("가장 먼저 만든 지도", "가장 나중 지도");
        assertThat(secondPage.getContent()).extracting(MapEntity::getName)
            .containsExactly("두 번째 지도");
        // 전체 개수는 조인 후 내 참여분만 세야 한다.
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
    }

    private void join(MapEntity map, Long userId) {
        mapMemberRepository.save(MapMember.of(map.getId(), userId, MapRole.MEMBER));
    }

    private void order(MapEntity map, Long userId, int sortOrder) {
        MapMember member = mapMemberRepository.findByMapIdAndUserId(map.getId(), userId).orElseThrow();
        member.changeSortOrder(sortOrder);
        mapMemberRepository.saveAndFlush(member);
    }

    /** 초대 코드는 유니크 제약이 있어 테스트마다 다른 값을 준다(컬럼 길이 12). */
    private MapEntity privateMap(String name) {
        return MapEntity.create(name, "설명", null, MapType.PRIVATE, OTHER_USER_ID, List.of(),
            "CODE%06d".formatted(++inviteCodeSeq));
    }
}
