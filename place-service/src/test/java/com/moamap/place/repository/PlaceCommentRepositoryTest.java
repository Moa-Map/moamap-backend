package com.moamap.place.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import com.moamap.place.entity.PlaceComment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

/**
 * PlaceComment.update()의 imageUrls 교체(clear+addAll)가 Hibernate가 실제로 관리하는
 * 컬렉션에서도 문제없이 flush되는지 확인한다. Mockito 단위 테스트로는 검증 불가능한 지점이다.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_schema=")
class PlaceCommentRepositoryTest {

    @Autowired
    private PlaceCommentRepository placeCommentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private PlaceComment.PlaceCommentBuilder commentBuilder() {
        return PlaceComment.builder()
            .placeId(1L)
            .userId(2L)
            .rating(5);
    }

    @Test
    void imageUrls를_저장하면_그대로_조회된다() {
        PlaceComment saved = placeCommentRepository.saveAndFlush(
            commentBuilder().imageUrls(new ArrayList<>(List.of("https://img/1.jpg"))).build());
        entityManager.clear();

        PlaceComment found = placeCommentRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();

        assertThat(found.getImageUrls()).containsExactly("https://img/1.jpg");
    }

    @Test
    void update로_imageUrls를_교체하면_실제_DB에도_반영된다() {
        PlaceComment saved = placeCommentRepository.saveAndFlush(
            commentBuilder().imageUrls(new ArrayList<>(List.of("https://img/1.jpg", "https://img/2.jpg"))).build());
        entityManager.clear();
        PlaceComment managed = placeCommentRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();

        managed.update(null, null, List.of("https://img/new.jpg"));
        placeCommentRepository.saveAndFlush(managed);
        entityManager.clear();

        PlaceComment found = placeCommentRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();
        assertThat(found.getImageUrls()).containsExactly("https://img/new.jpg");
    }

    @Test
    void update에_null을_넘기면_기존_imageUrls가_유지된다() {
        PlaceComment saved = placeCommentRepository.saveAndFlush(
            commentBuilder().imageUrls(new ArrayList<>(List.of("https://img/유지.jpg"))).build());
        entityManager.clear();
        PlaceComment managed = placeCommentRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();

        managed.update(4, "내용만 수정", null);
        placeCommentRepository.saveAndFlush(managed);
        entityManager.clear();

        PlaceComment found = placeCommentRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();
        assertThat(found.getImageUrls()).containsExactly("https://img/유지.jpg");
    }
}
