package com.moamap.place.repository;

import com.moamap.place.entity.PlaceCommentReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceCommentReportRepository extends JpaRepository<PlaceCommentReport, Long> {

    boolean existsByPlaceCommentIdAndReporterId(Long placeCommentId, Long reporterId);
}
