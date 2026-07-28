package com.moamap.place.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.entity.PlaceStatus;
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

    /*
     * 장소 사진(photoUrls)은 tags와 달리 "인덱스 0 = 대표 사진" 계약이 있어 순서가 결정적으로
     * 보존되어야 한다(청사진 5장). @OrderColumn(sort_order) 없이 순수 @ElementCollection(bag)만
     * 썼다면 재조회 시 순서가 뒤섞일 수 있으므로, 5장을 저장했다가 다시 읽어 순서가 그대로인지 확인한다.
     */

    @Test
    void 사진_URL을_저장하면_저장한_순서_그대로_조회된다() {
        List<String> photos = new ArrayList<>(List.of(
            "https://cdn.moamap.com/places/10/rep.jpg",
            "https://cdn.moamap.com/places/10/2.jpg",
            "https://cdn.moamap.com/places/10/3.jpg",
            "https://cdn.moamap.com/places/10/4.jpg",
            "https://cdn.moamap.com/places/10/5.jpg"
        ));
        Place saved = placeRepository.saveAndFlush(placeBuilder().photoUrls(photos).build());
        entityManager.clear();

        Place found = placeRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();

        assertThat(found.getPhotoUrls()).containsExactly(
            "https://cdn.moamap.com/places/10/rep.jpg",
            "https://cdn.moamap.com/places/10/2.jpg",
            "https://cdn.moamap.com/places/10/3.jpg",
            "https://cdn.moamap.com/places/10/4.jpg",
            "https://cdn.moamap.com/places/10/5.jpg"
        );
    }

    @Test
    void 사진이_없으면_빈_리스트로_저장되고_조회된다() {
        Place saved = placeRepository.saveAndFlush(placeBuilder().build());
        entityManager.clear();

        Place found = placeRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();

        assertThat(found.getPhotoUrls()).isEmpty();
    }

    /*
     * countByMapIdAndStatusAndDeletedAtIsNull은 place-service가 이벤트 발행 시점에
     * "목록 노출 대상" 절대 개수를 세는 쿼리다(청사진 2-2, 3-3(가) 5단계).
     * PENDING/REJECTED/소프트삭제는 어떤 경우에도 세면 안 된다(청사진 3-4 불변 조건).
     */

    @Test
    void countByMapIdAndStatusAndDeletedAtIsNull은_같은_지도의_APPROVED_미삭제_장소만_센다() {
        placeRepository.saveAndFlush(placeBuilder().kakaoPlaceId("A1").status(PlaceStatus.APPROVED).build());
        placeRepository.saveAndFlush(placeBuilder().kakaoPlaceId("A2").status(PlaceStatus.APPROVED).build());
        placeRepository.saveAndFlush(placeBuilder().kakaoPlaceId("P1").status(PlaceStatus.PENDING).build());
        placeRepository.saveAndFlush(placeBuilder().kakaoPlaceId("R1").status(PlaceStatus.REJECTED).build());
        Place softDeleted = placeRepository.saveAndFlush(
            placeBuilder().kakaoPlaceId("D1").status(PlaceStatus.APPROVED).build());
        softDeleted.delete();
        placeRepository.saveAndFlush(softDeleted);
        entityManager.clear();

        long count = placeRepository.countByMapIdAndStatusAndDeletedAtIsNull(10L, PlaceStatus.APPROVED);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void countByMapIdAndStatusAndDeletedAtIsNull은_다른_지도의_장소는_세지_않는다() {
        placeRepository.saveAndFlush(placeBuilder().kakaoPlaceId("A1").status(PlaceStatus.APPROVED).build());

        long count = placeRepository.countByMapIdAndStatusAndDeletedAtIsNull(999L, PlaceStatus.APPROVED);

        assertThat(count).isZero();
    }

    @Test
    void 대표_사진은_인덱스_0에_저장된_사진이다() {
        List<String> photos = new ArrayList<>(List.of(
            "https://cdn.moamap.com/places/10/rep.jpg",
            "https://cdn.moamap.com/places/10/2.jpg"
        ));
        Place saved = placeRepository.saveAndFlush(placeBuilder().photoUrls(photos).build());
        entityManager.clear();

        Place found = placeRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();

        assertThat(found.getPhotoUrls().get(0)).isEqualTo("https://cdn.moamap.com/places/10/rep.jpg");
    }
}
