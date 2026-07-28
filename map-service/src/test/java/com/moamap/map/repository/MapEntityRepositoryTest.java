package com.moamap.map.repository;

import java.util.List;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MapEntityRepositoryTest {

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
}
