package com.moamap.map.repository;

import java.util.List;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MapEntityRepositoryTest {

    private static final List<MapType> SEARCHABLE = List.of(MapType.COMMUNITY, MapType.OFFICIAL);

    @Autowired
    private MapEntityRepository mapEntityRepository;

    @Test
    void 존재하는_지도의_placeCount를_갱신하면_영향받은_행_수_1을_반환하고_값이_반영된다() {
        MapEntity map = mapEntityRepository.save(
            MapEntity.create("장소 개수 테스트 지도", "설명", null, MapType.COMMUNITY, 1L, List.of(), null));

        int updatedRows = mapEntityRepository.updatePlaceCount(map.getId(), 7L);

        assertThat(updatedRows).isEqualTo(1);
        MapEntity reloaded = mapEntityRepository.findById(map.getId()).orElseThrow();
        assertThat(reloaded.getPlaceCount()).isEqualTo(7);
    }

    @Test
    void 존재하지_않는_지도_id로_갱신하면_영향받은_행_수_0을_반환한다() {
        int updatedRows = mapEntityRepository.updatePlaceCount(999_999L, 7L);

        assertThat(updatedRows).isEqualTo(0);
    }

    @Test
    void 검색은_이름_설명_태그_어디에_걸려도_찾고_프라이빗_지도는_제외한다() {
        mapEntityRepository.save(MapEntity.create("성수 카페 투어", "설명", null, MapType.COMMUNITY, 1L, List.of(), null));
        mapEntityRepository.save(MapEntity.create("이름무관", "성수동 산책 코스", null, MapType.COMMUNITY, 1L, List.of(), null));
        mapEntityRepository.save(MapEntity.create("태그매칭", "설명", null, MapType.OFFICIAL, 1L, List.of("성수"), null));
        mapEntityRepository.save(MapEntity.create("성수 비밀 지도", "설명", null, MapType.PRIVATE, 1L, List.of(), null));

        Page<MapEntity> found = mapEntityRepository.search(SEARCHABLE, "%성수%", PageRequest.of(0, 20));

        assertThat(found.getContent()).extracting(MapEntity::getName)
            .containsExactlyInAnyOrder("성수 카페 투어", "이름무관", "태그매칭");
    }

    @Test
    void 태그가_여러_개인_지도도_검색_결과에서_한_건으로_센다() {
        mapEntityRepository.save(
            MapEntity.create("성수 카페 투어", "설명", null, MapType.COMMUNITY, 1L, List.of("카페", "성수", "데이트"), null));

        Page<MapEntity> found = mapEntityRepository.search(SEARCHABLE, "%성수%", PageRequest.of(0, 20));

        assertThat(found.getTotalElements()).isEqualTo(1);
        assertThat(found.getContent()).hasSize(1);
    }

    @Test
    void 대문자로_검색해도_소문자_이름을_찾는다() {
        mapEntityRepository.save(MapEntity.create("seoul cafe", "설명", null, MapType.COMMUNITY, 1L, List.of(), null));

        // 서비스가 검색어를 소문자로 낮춰 넘기므로 저장된 이름도 lower()로 맞춰 비교된다.
        Page<MapEntity> found = mapEntityRepository.search(SEARCHABLE, "%cafe%", PageRequest.of(0, 20));

        assertThat(found.getContent()).hasSize(1);
    }
}
