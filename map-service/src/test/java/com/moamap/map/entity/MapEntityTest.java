package com.moamap.map.entity;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapEntityTest {

    private MapEntity mapWithTags(List<String> tags) {
        return MapEntity.create("지도", "설명", null, MapType.COMMUNITY, 1L, tags, null);
    }

    @Test
    void 앞뒤_공백을_없앤다() {
        assertThat(mapWithTags(List.of(" 맛집", "카페 ", "  베이커리  ")).getTags())
            .containsExactly("맛집", "카페", "베이커리");
    }

    @Test
    void 앞에_붙은_해시를_없앤다() {
        assertThat(mapWithTags(List.of("#맛집", "##카페", "# 베이커리")).getTags())
            .containsExactly("맛집", "카페", "베이커리");
    }

    @Test
    void 영문_태그는_소문자로_맞춘다() {
        assertThat(mapWithTags(List.of("Cafe", "BRUNCH")).getTags())
            .containsExactly("cafe", "brunch");
    }

    @Test
    void 정규화_후_같아진_태그는_하나만_남기고_순서를_지킨다() {
        assertThat(mapWithTags(List.of("맛집", "#맛집", " 맛집 ", "카페")).getTags())
            .containsExactly("맛집", "카페");
    }

    @Test
    void 빈_태그와_null_태그는_버린다() {
        List<String> tags = new ArrayList<>(List.of("맛집", "", "   ", "#"));
        tags.add(null);

        assertThat(mapWithTags(tags).getTags()).containsExactly("맛집");
    }

    @Test
    void 태그가_null이면_빈_목록이_된다() {
        assertThat(mapWithTags(null).getTags()).isEmpty();
    }

    @Test
    void 수정할_때도_같은_규칙이_적용된다() {
        MapEntity map = mapWithTags(List.of("맛집"));

        map.update("지도", "설명", null, List.of(" #Cafe ", "cafe"));

        assertThat(map.getTags()).containsExactly("cafe");
    }
}
