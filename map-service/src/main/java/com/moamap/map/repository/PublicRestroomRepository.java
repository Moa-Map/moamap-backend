package com.moamap.map.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.moamap.map.entity.GeocodeStatus;
import com.moamap.map.entity.PublicRestroom;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PublicRestroomRepository extends JpaRepository<PublicRestroom, Long> {

    // 지오코딩 대기열 청크 크기. 첫 배치는 전량(약 5만 건)이 PENDING이라, 상한 없이 조회하면 통째로 힙에 올라간다.
    int GEOCODE_QUEUE_CHUNK_SIZE = 1000;

    Optional<PublicRestroom> findByMngNo(String mngNo);

    Optional<PublicRestroom> findByIdAndDeletedAtIsNullAndHiddenFalse(Long id);

    long countByDeletedAtIsNull();

    /**
     * 지오코딩 대기열의 첫 청크. 처리된 행은 상태가 PENDING에서 벗어나므로, 같은 조건으로 다시 호출해도
     * 자연히 남은 대기열의 앞부분을 반환한다 — 별도 오프셋 관리가 필요 없다.
     */
    default List<PublicRestroom> findByGeocodeStatusAndDeletedAtIsNullOrderByIdAsc(GeocodeStatus geocodeStatus) {
        return findByGeocodeStatusAndDeletedAtIsNullOrderByIdAsc(geocodeStatus,
            PageRequest.of(0, GEOCODE_QUEUE_CHUNK_SIZE));
    }

    List<PublicRestroom> findByGeocodeStatusAndDeletedAtIsNullOrderByIdAsc(GeocodeStatus geocodeStatus,
        Pageable pageable);

    /**
     * 카카오 검색 실패로 PENDING인 채 남는 행이 있으면 위 메서드가 매번 같은 앞부분을 반환해 무한 루프가 될 수 있다.
     * 이 메서드로 마지막으로 처리한 id 이후만 조회해 대기열을 앞으로 진행시킨다.
     */
    List<PublicRestroom> findByGeocodeStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
        GeocodeStatus geocodeStatus, Long id, Pageable pageable);

    @Query("select r from PublicRestroom r where r.deletedAt is null and r.hidden = false and r.lat is not null and r.lng is not null "
        + "and r.lat between :swLat and :neLat and r.lng between :swLng and :neLng order by r.id asc")
    List<PublicRestroom> findMarkersInBounds(@Param("swLat") BigDecimal swLat, @Param("swLng") BigDecimal swLng,
        @Param("neLat") BigDecimal neLat, @Param("neLng") BigDecimal neLng, Pageable pageable);

    /**
     * 원천에 없던 활성 행을 정리하는 삭제 가드. 이미 삭제된 행은 조건(deletedAt is null)에서 빠지므로
     * deletedAt이 새 값으로 덮이지 않는다.
     */
    // @Modifying 쿼리는 트랜잭션이 있어야 실행된다. 서비스 계층은 배치 전체를 한 트랜잭션으로 묶지 않으므로
    // (수만 건 순회 + 외부 호출 중 하나가 이 상태로 묶이면 안 됨), 이 벌크 UPDATE 한 문장만 여기서 트랜잭션을 연다.
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update PublicRestroom r set r.deletedAt = :deletedAt "
        + "where r.deletedAt is null and r.lastSyncedAt < :cutoff")
    int softDeleteStaleActive(@Param("cutoff") LocalDateTime cutoff, @Param("deletedAt") LocalDateTime deletedAt);
}
