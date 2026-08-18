package com.moamap.place.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByIdAndDeletedAtIsNull(Long id);

    Page<Place> findByMapIdAndStatusAndDeletedAtIsNull(Long mapId, PlaceStatus status, Pageable pageable);

    Page<Place> findByMapIdAndStatusAndCreatedByAndDeletedAtIsNull(
        Long mapId, PlaceStatus status, Long createdBy, Pageable pageable);

    boolean existsByMapIdAndKakaoPlaceIdAndDeletedAtIsNull(Long mapId, String kakaoPlaceId);

    /**
     * 리뷰 요약값(avgRating/commentCount)은 리뷰 테이블에서 매번 다시 계산하는 파생값이라 낙관적 락 충돌이 의미가 없다.
     * 벌크 업데이트로 엔티티 로드·버전 체크를 건너뛰어, 리뷰 동시 작성이 Place의 @Version과 충돌하지 않게 한다.
     */
    @Modifying
    @Query("update Place p set p.avgRating = :avgRating, p.commentCount = :commentCount where p.id = :placeId")
    void updateReviewSummary(@Param("placeId") Long placeId, @Param("avgRating") BigDecimal avgRating,
            @Param("commentCount") int commentCount);

    /** 목록 노출 대상(APPROVED·미삭제)의 현재 절대 개수. 이벤트 발행 시점에 조회한다(청사진 2-2, 3-3(가)). */
    long countByMapIdAndStatusAndDeletedAtIsNull(Long mapId, PlaceStatus status);

    /**
     * 지도 안에서 멤버별로 등록한 장소 수를 한 번에 센다. 멤버가 몇 명이든 쿼리 1회다.
     *
     * countByMapIdAndStatusAndDeletedAtIsNull와 같은 APPROVED·미삭제 기준이라,
     * 멤버별 합이 지도 총 장소 수를 넘지 않는다.
     */
    @Query("select p.createdBy as createdBy, count(p) as placeCount from Place p "
        + "where p.mapId = :mapId and p.status = com.moamap.place.entity.PlaceStatus.APPROVED "
        + "and p.deletedAt is null and p.createdBy in :userIds "
        + "group by p.createdBy")
    List<PlaceCountByCreator> countApprovedByCreator(
        @Param("mapId") Long mapId, @Param("userIds") Collection<Long> userIds);
}
