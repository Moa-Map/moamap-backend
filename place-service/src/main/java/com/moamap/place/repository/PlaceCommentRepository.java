package com.moamap.place.repository;

import java.util.Optional;
import com.moamap.place.entity.PlaceComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceCommentRepository extends JpaRepository<PlaceComment, Long> {

    Optional<PlaceComment> findByIdAndDeletedAtIsNull(Long id);

    Page<PlaceComment> findByPlaceIdAndDeletedAtIsNull(Long placeId, Pageable pageable);

    long countByPlaceIdAndDeletedAtIsNull(Long placeId);

    // JPA의 avg()는 스펙상 항상 Double을 반환한다. BigDecimal로 선언하면 런타임에 타입 캐스팅 오류가 난다.
    @Query("select coalesce(avg(r.rating), 0) from PlaceComment r where r.placeId = :placeId and r.deletedAt is null")
    Double averageRatingByPlaceId(@Param("placeId") Long placeId);
}
