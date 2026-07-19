package com.moamap.place.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceSourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

/**
 * Mockito로는 검증 못 하는, 실제 Hibernate/DB 동작을 확인한다:
 * - uk_places_map_kakao_place 유니크 제약이 진짜로 중복을 막는지
 * - tags(@ElementCollection)가 실제로 저장·조회되는지
 * - Place.update()의 태그 교체(clear+addAll)가 Hibernate가 관리하는 컬렉션에서도 문제없이 flush되는지
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_schema=")
class PlaceRepositoryTest {

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Place.PlaceBuilder placeBuilder() {
        return Place.builder()
            .name("스타벅스 강남점")
            .lat(BigDecimal.valueOf(37.497852))
            .lng(BigDecimal.valueOf(127.027618))
            .kakaoPlaceId("26338954")
            .sourceType(PlaceSourceType.KAKAO_SEARCH)
            .mapId(10L)
            .createdBy(1L);
    }

    @Test
    void 같은_지도에_같은_kakaoPlaceId는_중복_저장되지_않는다() {
        placeRepository.saveAndFlush(placeBuilder().build());

        assertThatThrownBy(() -> placeRepository.saveAndFlush(placeBuilder().build()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 삭제된_장소는_kakaoPlaceId가_비워져서_같은_지도에_재등록할_수_있다() {
        Place original = placeRepository.saveAndFlush(placeBuilder().build());
        entityManager.clear();
        Place managed = placeRepository.findByIdAndDeletedAtIsNull(original.getId()).orElseThrow();

        managed.delete();
        placeRepository.saveAndFlush(managed);
        entityManager.clear();

        // 삭제된 장소는 더 이상 findByIdAndDeletedAtIsNull로 조회되지 않는다.
        assertThat(placeRepository.findByIdAndDeletedAtIsNull(original.getId())).isEmpty();
        // 유니크 제약이 kakaoPlaceId=null끼리는 충돌시키지 않으므로, 같은 kakaoPlaceId로 재등록이 가능하다.
        assertThatCode(() -> placeRepository.saveAndFlush(placeBuilder().build()))
            .doesNotThrowAnyException();
    }

    @Test
    void 태그를_저장하면_그대로_조회된다() {
        Place saved = placeRepository.saveAndFlush(
            placeBuilder().tags(new ArrayList<>(List.of("데이트", "조용한"))).build());
        entityManager.clear();

        Place found = placeRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();

        assertThat(found.getTags()).containsExactly("데이트", "조용한");
    }

    @Test
    void update로_태그를_교체하면_실제_DB에도_반영된다() {
        Place saved = placeRepository.saveAndFlush(
            placeBuilder().tags(new ArrayList<>(List.of("old1", "old2"))).build());
        entityManager.clear();
        Place managed = placeRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();

        managed.update(null, null, null, null, null, null, null, List.of("new1"));
        placeRepository.saveAndFlush(managed);
        entityManager.clear();

        Place found = placeRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();
        assertThat(found.getTags()).containsExactly("new1");
    }

    @Test
    void update에_null을_넘기면_기존_태그가_유지된다() {
        Place saved = placeRepository.saveAndFlush(
            placeBuilder().tags(new ArrayList<>(List.of("유지됨"))).build());
        entityManager.clear();
        Place managed = placeRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();

        managed.update("새 이름", null, null, null, null, null, null, null);
        placeRepository.saveAndFlush(managed);
        entityManager.clear();

        Place found = placeRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();
        assertThat(found.getTags()).containsExactly("유지됨");
    }
}
