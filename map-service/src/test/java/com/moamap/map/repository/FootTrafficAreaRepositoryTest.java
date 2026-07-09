package com.moamap.map.repository;

import java.time.LocalDateTime;
import java.util.List;
import com.moamap.map.entity.FootTrafficArea;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest}는 JPA 관련 빈만 띄우고 내장 H2로 실제 DB에 저장·조회한다.
 * "DB에 실제로 값이 들어가고, 그대로 나오는가"를 검증하는 테스트.
 * <p>
 * FootTrafficArea는 seed로만 채워지는 읽기 전용 엔티티라 생성자/setter가 없다 —
 * 그래서 테스트에서는 리플렉션(BeanUtils + ReflectionTestUtils)으로 필드를 채운다.
 * 프로덕션 코드는 이 방식으로 객체를 안 만든다(항상 DB에서 로드됨).
 */
@DataJpaTest
class FootTrafficAreaRepositoryTest {

    @Autowired
    private FootTrafficAreaRepository footTrafficAreaRepository;

    @Test
    void 저장한_유동인구_관측지점을_다시_조회하면_그대로_나온다() {
        // Arrange
        FootTrafficArea area = newFootTrafficArea("POI001", "강남 MICE 관광특구", "관광특구");

        // Act
        footTrafficAreaRepository.save(area);
        List<FootTrafficArea> found = footTrafficAreaRepository.findAll();

        // Assert
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getFootTrafficAreaCd()).isEqualTo("POI001");
        assertThat(found.get(0).getAreaNm()).isEqualTo("강남 MICE 관광특구");
        assertThat(found.get(0).getCategory()).isEqualTo("관광특구");
    }

    @Test
    void 아무것도_저장하지_않으면_조회_결과가_비어있다() {
        // Act
        List<FootTrafficArea> found = footTrafficAreaRepository.findAll();

        // Assert
        assertThat(found).isEmpty();
    }

    private FootTrafficArea newFootTrafficArea(String cd, String areaNm, String category) {
        FootTrafficArea area = BeanUtils.instantiateClass(FootTrafficArea.class);
        ReflectionTestUtils.setField(area, "footTrafficAreaCd", cd);
        ReflectionTestUtils.setField(area, "areaNm", areaNm);
        ReflectionTestUtils.setField(area, "engNm", "Test Zone");
        ReflectionTestUtils.setField(area, "category", category);
        ReflectionTestUtils.setField(area, "createdAt", LocalDateTime.now());
        return area;
    }
}
