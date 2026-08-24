package com.moamap.map.repository;

import java.util.List;
import com.moamap.map.config.JpaAuditingConfig;
import com.moamap.map.entity.MapPost;
import com.moamap.map.entity.PlaceTag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Mockito로는 확인할 수 없는 실제 Hibernate/DB 동작을 검증한다:
 * - @OrderColumn이 사진 순서를 실제로 보존하는지
 * - map_post_place_tags 복합 PK가 같은 장소 중복 태그를 진짜로 막는지
 * - 소프트 삭제된 게시물이 목록에서 빠지는지
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class MapPostRepositoryTest {

    @Autowired
    private MapPostRepository mapPostRepository;

    @Autowired
    private TestEntityManager entityManager;

    private MapPost post(List<String> imageUrls, List<PlaceTag> placeTags) {
        return MapPost.create(10L, 2L, "성수 카페 다녀왔어요", imageUrls, placeTags);
    }

    @Test
    void 사진은_저장한_순서대로_조회된다() {
        MapPost saved = mapPostRepository.saveAndFlush(post(List.of(
            "https://cdn.moamap.com/map-posts/10/1.jpg",
            "https://cdn.moamap.com/map-posts/10/2.jpg",
            "https://cdn.moamap.com/map-posts/10/3.jpg"), List.of()));
        entityManager.clear();

        MapPost found = mapPostRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();

        assertThat(found.getImageUrls()).containsExactly(
            "https://cdn.moamap.com/map-posts/10/1.jpg",
            "https://cdn.moamap.com/map-posts/10/2.jpg",
            "https://cdn.moamap.com/map-posts/10/3.jpg");
    }

    @Test
    void 장소_태그는_id와_이름_스냅샷이_함께_저장된다() {
        MapPost saved = mapPostRepository.saveAndFlush(post(List.of(), List.of(
            new PlaceTag(5L, "블루보틀 성수점"),
            new PlaceTag(6L, "대림창고"))));
        entityManager.clear();

        MapPost found = mapPostRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();

        assertThat(found.getPlaceTags())
            .extracting(PlaceTag::placeId, PlaceTag::placeName)
            .containsExactlyInAnyOrder(tuple(5L, "블루보틀 성수점"), tuple(6L, "대림창고"));
    }

    @Test
    void 같은_게시물에_같은_장소를_두_번_태그할_수_없다() {
        MapPost duplicated = post(List.of(), List.of(
            new PlaceTag(5L, "블루보틀 성수점"),
            new PlaceTag(5L, "블루보틀 성수점")));

        assertThatThrownBy(() -> mapPostRepository.saveAndFlush(duplicated))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 소프트_삭제된_게시물은_목록과_단건_조회에서_모두_빠진다() {
        MapPost saved = mapPostRepository.saveAndFlush(post(List.of(), List.of()));
        mapPostRepository.saveAndFlush(post(List.of(), List.of()));
        saved.delete();
        mapPostRepository.saveAndFlush(saved);
        entityManager.clear();

        Page<MapPost> page = mapPostRepository.findByMapIdAndDeletedAtIsNull(10L, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(mapPostRepository.findByIdAndDeletedAtIsNull(saved.getId())).isEmpty();
    }

    @Test
    void 사진과_태그를_수정하면_기존_행이_교체된다() {
        MapPost saved = mapPostRepository.saveAndFlush(post(
            List.of("https://cdn.moamap.com/map-posts/10/1.jpg"),
            List.of(new PlaceTag(5L, "블루보틀 성수점"))));
        entityManager.clear();

        MapPost managed = mapPostRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();
        managed.update(null,
            List.of("https://cdn.moamap.com/map-posts/10/9.jpg"),
            List.of(new PlaceTag(7L, "성수동 우육면관")));
        mapPostRepository.saveAndFlush(managed);
        entityManager.clear();

        MapPost found = mapPostRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();
        assertThat(found.getImageUrls()).containsExactly("https://cdn.moamap.com/map-posts/10/9.jpg");
        assertThat(found.getPlaceTags()).extracting(PlaceTag::placeId).containsExactly(7L);
    }
}
