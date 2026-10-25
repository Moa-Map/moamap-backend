package com.moamap.place.repository;

import com.moamap.place.entity.PlaceReport;
import com.moamap.place.entity.ReportReason;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceReportRepository extends JpaRepository<PlaceReport, Long> {

    boolean existsByPlaceIdAndReporterId(Long placeId, Long reporterId);

    boolean existsByPlaceId(Long placeId);

    Page<PlaceReport> findAllBy(Pageable pageable);

    /**
     * 방장·관리자가 보는 지도의 신고된 장소. 삭제된 장소는 이미 처리된 것이라 빼고, 신고 많은 순 → 최근 신고 순이다.
     */
    @Query(value = """
        select r.placeId as placeId, count(r) as reportCount, max(r.createdAt) as lastReportedAt
        from PlaceReport r, Place p
        where r.mapId = :mapId and p.id = r.placeId and p.deletedAt is null
        group by r.placeId
        order by count(r) desc, max(r.createdAt) desc
        """,
        countQuery = """
        select count(distinct r.placeId) from PlaceReport r, Place p
        where r.mapId = :mapId and p.id = r.placeId and p.deletedAt is null
        """)
    Page<ReportedPlaceRow> findReportedPlaces(@Param("mapId") Long mapId, Pageable pageable);

    @Query("select r.placeId as placeId, r.reason as reason, count(r) as reportCount from PlaceReport r "
        + "where r.placeId in :placeIds group by r.placeId, r.reason")
    List<ReasonCountRow> countReasons(@Param("placeIds") Collection<Long> placeIds);

    @Query("select r.placeId as placeId, count(r) as reportCount from PlaceReport r "
        + "where r.placeId in :placeIds group by r.placeId")
    List<PlaceCountRow> countByPlaceIds(@Param("placeIds") Collection<Long> placeIds);

    interface ReportedPlaceRow {
        Long getPlaceId();

        long getReportCount();

        LocalDateTime getLastReportedAt();
    }

    interface ReasonCountRow {
        Long getPlaceId();

        ReportReason getReason();

        long getReportCount();
    }

    interface PlaceCountRow {
        Long getPlaceId();

        long getReportCount();
    }
}
