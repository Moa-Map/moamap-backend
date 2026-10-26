package com.moamap.place.repository;

import com.moamap.place.entity.PlaceCommentReport;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceCommentReportRepository extends JpaRepository<PlaceCommentReport, Long> {

    boolean existsByPlaceCommentIdAndReporterId(Long placeCommentId, Long reporterId);

    Page<PlaceCommentReport> findAllBy(Pageable pageable);

    @Query("select r.placeCommentId as placeCommentId, count(r) as reportCount from PlaceCommentReport r "
        + "where r.placeCommentId in :commentIds group by r.placeCommentId")
    List<CommentCountRow> countByCommentIds(@Param("commentIds") Collection<Long> commentIds);

    interface CommentCountRow {
        Long getPlaceCommentId();

        long getReportCount();
    }
}
