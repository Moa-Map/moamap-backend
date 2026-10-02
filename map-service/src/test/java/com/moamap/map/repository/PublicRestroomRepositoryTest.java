package com.moamap.map.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.moamap.map.config.JpaAuditingConfig;
import com.moamap.map.entity.GeocodeStatus;
import com.moamap.map.entity.PublicRestroom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} — 내장 H2로 쿼리 메서드 동작을 검증한다.
 * 청사진 3-1 (F)(G) 표와 소프트 삭제 벌크 업데이트 커버.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class PublicRestroomRepositoryTest {

    @Autowired
    private PublicRestroomRepository publicRestroomRepository;

    @Test
    void mngNo로_저장한_행을_조회하면_그대로_나온다() {
        // Arrange
        publicRestroomRepository.save(activeRestroom("MNG001", "역삼동 공중화장실",
            new BigDecimal("37.500000"), new BigDecimal("127.030000"), GeocodeStatus.OK));

        // Act
        Optional<PublicRestroom> found = publicRestroomRepository.findByMngNo("MNG001");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("역삼동 공중화장실");
    }

    @Test
    void 존재하지_않는_mngNo로_조회하면_비어있다() {
        // Act
        Optional<PublicRestroom> found = publicRestroomRepository.findByMngNo("NOT_EXIST");

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    void 삭제되지_않은_id로_조회하면_행이_나온다() {
        // Arrange
        PublicRestroom saved = publicRestroomRepository.save(activeRestroom("MNG002", "상세조회용",
            new BigDecimal("37.500000"), new BigDecimal("127.030000"), GeocodeStatus.OK));

        // Act
        Optional<PublicRestroom> found = publicRestroomRepository.findByIdAndDeletedAtIsNullAndHiddenFalse(saved.getId());

        // Assert
        assertThat(found).isPresent();
    }

    @Test
    void 소프트_삭제된_id로_조회하면_비어있다() {
        // Arrange
        PublicRestroom deleted = deletedRestroom("MNG003", "삭제된곳",
            new BigDecimal("37.500000"), new BigDecimal("127.030000"), GeocodeStatus.OK);
        PublicRestroom saved = publicRestroomRepository.save(deleted);

        // Act
        Optional<PublicRestroom> found = publicRestroomRepository.findByIdAndDeletedAtIsNullAndHiddenFalse(saved.getId());

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    void 숨긴_id로_상세_조회하면_비어있다() {
        // Arrange
        PublicRestroom hidden = activeRestroom("MNG004", "숨긴곳",
            new BigDecimal("37.500000"), new BigDecimal("127.030000"), GeocodeStatus.OK);
        ReflectionTestUtils.setField(hidden, "hidden", true);
        PublicRestroom saved = publicRestroomRepository.save(hidden);

        // Act
        Optional<PublicRestroom> found = publicRestroomRepository.findByIdAndDeletedAtIsNullAndHiddenFalse(saved.getId());

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    void bbox_조회는_숨긴_행을_제외한다() {
        // Arrange
        PublicRestroom hidden = activeRestroom("MNG005", "숨긴곳",
            new BigDecimal("37.505000"), new BigDecimal("127.035000"), GeocodeStatus.OK);
        ReflectionTestUtils.setField(hidden, "hidden", true);
        publicRestroomRepository.save(hidden);

        // Act
        List<PublicRestroom> found = publicRestroomRepository.findMarkersInBounds(
            new BigDecimal("37.50"), new BigDecimal("127.03"),
            new BigDecimal("37.51"), new BigDecimal("127.04"),
            PageRequest.of(0, 501));

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    void 삭제되지_않은_행만_카운트한다() {
        // Arrange
        publicRestroomRepository.save(activeRestroom("MNG010", "A",
            new BigDecimal("37.500000"), new BigDecimal("127.030000"), GeocodeStatus.OK));
        publicRestroomRepository.save(activeRestroom("MNG011", "B",
            new BigDecimal("37.501000"), new BigDecimal("127.031000"), GeocodeStatus.OK));
        publicRestroomRepository.save(deletedRestroom("MNG012", "C",
            new BigDecimal("37.502000"), new BigDecimal("127.032000"), GeocodeStatus.OK));

        // Act
        long count = publicRestroomRepository.countByDeletedAtIsNull();

        // Assert
        assertThat(count).isEqualTo(2);
    }

    @Test
    void bbox_경계_안의_행만_반환하고_밖의_행은_제외한다() {
        // Arrange — bbox: (37.50, 127.03) ~ (37.51, 127.04)
        PublicRestroom inside = publicRestroomRepository.save(activeRestroom("MNG020", "안쪽",
            new BigDecimal("37.505000"), new BigDecimal("127.035000"), GeocodeStatus.OK));
        publicRestroomRepository.save(activeRestroom("MNG021", "바깥쪽",
            new BigDecimal("37.600000"), new BigDecimal("127.100000"), GeocodeStatus.OK));

        // Act
        List<PublicRestroom> found = publicRestroomRepository.findMarkersInBounds(
            new BigDecimal("37.50"), new BigDecimal("127.03"),
            new BigDecimal("37.51"), new BigDecimal("127.04"),
            PageRequest.of(0, 501));

        // Assert
        assertThat(found).extracting(PublicRestroom::getId).containsExactly(inside.getId());
    }

    @Test
    void bbox_경계값_포함_여부는_포함이다() {
        // Arrange — 정확히 경계선 위에 있는 좌표
        PublicRestroom onBoundary = publicRestroomRepository.save(activeRestroom("MNG022", "경계",
            new BigDecimal("37.500000"), new BigDecimal("127.030000"), GeocodeStatus.OK));

        // Act
        List<PublicRestroom> found = publicRestroomRepository.findMarkersInBounds(
            new BigDecimal("37.50"), new BigDecimal("127.03"),
            new BigDecimal("37.51"), new BigDecimal("127.04"),
            PageRequest.of(0, 501));

        // Assert
        assertThat(found).extracting(PublicRestroom::getId).contains(onBoundary.getId());
    }

    @Test
    void bbox_조회는_소프트_삭제된_행을_제외한다() {
        // Arrange
        publicRestroomRepository.save(deletedRestroom("MNG023", "삭제됨",
            new BigDecimal("37.505000"), new BigDecimal("127.035000"), GeocodeStatus.OK));

        // Act
        List<PublicRestroom> found = publicRestroomRepository.findMarkersInBounds(
            new BigDecimal("37.50"), new BigDecimal("127.03"),
            new BigDecimal("37.51"), new BigDecimal("127.04"),
            PageRequest.of(0, 501));

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    void bbox_조회는_좌표가_없는_PENDING_또는_FAILED_행을_제외한다() {
        // Arrange
        publicRestroomRepository.save(pendingRestroomWithoutCoords("MNG024", "지오코딩전"));
        publicRestroomRepository.save(failedRestroomWithoutCoords("MNG025", "지오코딩실패"));

        // Act
        List<PublicRestroom> found = publicRestroomRepository.findMarkersInBounds(
            new BigDecimal("-90"), new BigDecimal("-180"),
            new BigDecimal("90"), new BigDecimal("180"),
            PageRequest.of(0, 501));

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    void swLat가_neLat보다_크면_역전된_범위라_빈_목록을_반환한다() {
        // Arrange
        publicRestroomRepository.save(activeRestroom("MNG026", "정상범위내",
            new BigDecimal("37.505000"), new BigDecimal("127.035000"), GeocodeStatus.OK));

        // Act — swLat(37.51) > neLat(37.50)로 역전
        List<PublicRestroom> found = publicRestroomRepository.findMarkersInBounds(
            new BigDecimal("37.51"), new BigDecimal("127.03"),
            new BigDecimal("37.50"), new BigDecimal("127.04"),
            PageRequest.of(0, 501));

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    void bbox_조회는_id_오름차순으로_정렬되고_Pageable_크기만큼만_반환한다() {
        // Arrange — id 역순이 아니라 자연 증가 순으로 3건 저장
        PublicRestroom r1 = publicRestroomRepository.save(activeRestroom("MNG030", "1번",
            new BigDecimal("37.505000"), new BigDecimal("127.035000"), GeocodeStatus.OK));
        PublicRestroom r2 = publicRestroomRepository.save(activeRestroom("MNG031", "2번",
            new BigDecimal("37.506000"), new BigDecimal("127.036000"), GeocodeStatus.OK));
        publicRestroomRepository.save(activeRestroom("MNG032", "3번",
            new BigDecimal("37.507000"), new BigDecimal("127.037000"), GeocodeStatus.OK));

        Pageable limitTwo = PageRequest.of(0, 2);

        // Act
        List<PublicRestroom> found = publicRestroomRepository.findMarkersInBounds(
            new BigDecimal("37.50"), new BigDecimal("127.03"),
            new BigDecimal("37.51"), new BigDecimal("127.04"),
            limitTwo);

        // Assert — 상한(2)만큼만, id 오름차순
        assertThat(found).extracting(PublicRestroom::getId).containsExactly(r1.getId(), r2.getId());
    }

    @Test
    void 소프트_삭제_벌크_업데이트는_cutoff_이전에_동기화된_활성_행만_삭제한다() {
        // Arrange
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 1, 4, 0);
        PublicRestroom stale = publicRestroomRepository.save(
            withLastSyncedAt("MNG040", "정리대상", cutoff.minusDays(40)));
        PublicRestroom fresh = publicRestroomRepository.save(
            withLastSyncedAt("MNG041", "이번에도수집됨", cutoff));
        PublicRestroom alreadyDeleted = publicRestroomRepository.save(
            deletedRestroomWithLastSyncedAt("MNG042", "이미삭제됨", cutoff.minusDays(40)));

        LocalDateTime deletedAt = LocalDateTime.of(2026, 9, 1, 4, 5);

        // Act
        int updated = publicRestroomRepository.softDeleteStaleActive(cutoff, deletedAt);

        // Assert
        assertThat(updated).isEqualTo(1);

        PublicRestroom staleReloaded = publicRestroomRepository.findByMngNo("MNG040").orElseThrow();
        assertThat(staleReloaded.getDeletedAt()).isEqualTo(deletedAt);

        PublicRestroom freshReloaded = publicRestroomRepository.findByMngNo("MNG041").orElseThrow();
        assertThat(freshReloaded.getDeletedAt()).isNull();

        // 이미 삭제된 행은 조건(deleted_at IS NULL)에서 빠지므로 deletedAt이 새 값으로 덮이지 않는다
        PublicRestroom alreadyDeletedReloaded = publicRestroomRepository.findByMngNo("MNG042").orElseThrow();
        assertThat(alreadyDeletedReloaded.getDeletedAt()).isEqualTo(alreadyDeleted.getDeletedAt());
        assertThat(alreadyDeletedReloaded.getDeletedAt()).isNotEqualTo(deletedAt);
    }

    private PublicRestroom activeRestroom(String mngNo, String name, BigDecimal lat, BigDecimal lng,
            GeocodeStatus status) {
        return PublicRestroom.builder()
            .mngNo(mngNo)
            .name(name)
            .lat(lat)
            .lng(lng)
            .geocodeStatus(status)
            .maleToilet((short) 0)
            .maleUrinal((short) 0)
            .maleDisabledToilet((short) 0)
            .maleDisabledUrinal((short) 0)
            .maleChildToilet((short) 0)
            .maleChildUrinal((short) 0)
            .femaleToilet((short) 0)
            .femaleDisabledToilet((short) 0)
            .femaleChildToilet((short) 0)
            .lastSyncedAt(LocalDateTime.now())
            .build();
    }

    private PublicRestroom deletedRestroom(String mngNo, String name, BigDecimal lat, BigDecimal lng,
            GeocodeStatus status) {
        PublicRestroom restroom = activeRestroom(mngNo, name, lat, lng, status);
        ReflectionTestUtils.setField(restroom, "deletedAt", LocalDateTime.now());
        return restroom;
    }

    private PublicRestroom pendingRestroomWithoutCoords(String mngNo, String name) {
        return PublicRestroom.builder()
            .mngNo(mngNo)
            .name(name)
            .lat(null)
            .lng(null)
            .geocodeStatus(GeocodeStatus.PENDING)
            .maleToilet((short) 0)
            .maleUrinal((short) 0)
            .maleDisabledToilet((short) 0)
            .maleDisabledUrinal((short) 0)
            .maleChildToilet((short) 0)
            .maleChildUrinal((short) 0)
            .femaleToilet((short) 0)
            .femaleDisabledToilet((short) 0)
            .femaleChildToilet((short) 0)
            .lastSyncedAt(LocalDateTime.now())
            .build();
    }

    private PublicRestroom failedRestroomWithoutCoords(String mngNo, String name) {
        return PublicRestroom.builder()
            .mngNo(mngNo)
            .name(name)
            .lat(null)
            .lng(null)
            .geocodeStatus(GeocodeStatus.FAILED)
            .maleToilet((short) 0)
            .maleUrinal((short) 0)
            .maleDisabledToilet((short) 0)
            .maleDisabledUrinal((short) 0)
            .maleChildToilet((short) 0)
            .maleChildUrinal((short) 0)
            .femaleToilet((short) 0)
            .femaleDisabledToilet((short) 0)
            .femaleChildToilet((short) 0)
            .lastSyncedAt(LocalDateTime.now())
            .build();
    }

    private PublicRestroom withLastSyncedAt(String mngNo, String name, LocalDateTime lastSyncedAt) {
        return PublicRestroom.builder()
            .mngNo(mngNo)
            .name(name)
            .lat(new BigDecimal("37.500000"))
            .lng(new BigDecimal("127.030000"))
            .geocodeStatus(GeocodeStatus.OK)
            .maleToilet((short) 0)
            .maleUrinal((short) 0)
            .maleDisabledToilet((short) 0)
            .maleDisabledUrinal((short) 0)
            .maleChildToilet((short) 0)
            .maleChildUrinal((short) 0)
            .femaleToilet((short) 0)
            .femaleDisabledToilet((short) 0)
            .femaleChildToilet((short) 0)
            .lastSyncedAt(lastSyncedAt)
            .build();
    }

    private PublicRestroom deletedRestroomWithLastSyncedAt(String mngNo, String name, LocalDateTime lastSyncedAt) {
        PublicRestroom restroom = withLastSyncedAt(mngNo, name, lastSyncedAt);
        ReflectionTestUtils.setField(restroom, "deletedAt", LocalDateTime.of(2026, 8, 1, 0, 0));
        return restroom;
    }
}
