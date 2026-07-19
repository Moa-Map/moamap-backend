package com.moamap.place.repository;

import java.math.BigDecimal;
import java.util.Optional;
import com.moamap.place.entity.PlaceReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceReviewRepository extends JpaRepository<PlaceReview, Long> {

    Optional<PlaceReview> findByIdAndDeletedAtIsNull(Long id);

    Page<PlaceReview> findByPlaceIdAndDeletedAtIsNull(Long placeId, Pageable pageable);

    long countByPlaceIdAndDeletedAtIsNull(Long placeId);

    @Query("select coalesce(avg(r.rating), 0) from PlaceReview r where r.placeId = :placeId and r.deletedAt is null")
    BigDecimal averageRatingByPlaceId(@Param("placeId") Long placeId);
}
