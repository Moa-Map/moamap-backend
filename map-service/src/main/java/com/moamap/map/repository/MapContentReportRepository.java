package com.moamap.map.repository;

import java.util.Collection;
import java.util.List;
import com.moamap.map.entity.MapContentReport;
import com.moamap.map.entity.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapContentReportRepository extends JpaRepository<MapContentReport, Long> {

    boolean existsByTargetTypeAndTargetIdAndReporterId(ReportTargetType targetType, Long targetId, Long reporterId);

    Page<MapContentReport> findByTargetType(ReportTargetType targetType, Pageable pageable);

    Page<MapContentReport> findAllBy(Pageable pageable);

    /** 대상별 누적 신고 수. 목록 한 페이지의 대상들을 한 번에 센다. */
    @Query("select r.targetType as targetType, r.targetId as targetId, count(r) as reportCount "
        + "from MapContentReport r where r.targetType = :targetType and r.targetId in :targetIds "
        + "group by r.targetType, r.targetId")
    List<TargetReportCount> countByTargets(@Param("targetType") ReportTargetType targetType,
                                           @Param("targetIds") Collection<Long> targetIds);

    interface TargetReportCount {
        ReportTargetType getTargetType();

        Long getTargetId();

        long getReportCount();
    }
}
