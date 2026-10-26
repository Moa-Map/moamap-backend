package com.moamap.user.report.repository;

import com.moamap.user.report.entity.UserReport;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserReportRepository extends JpaRepository<UserReport, Long> {

    boolean existsByTargetUserIdAndReporterId(Long targetUserId, Long reporterId);

    /** 대상별 누적 신고 수. 목록 한 페이지의 대상들을 한 번에 센다. */
    @Query("select r.targetUserId as targetUserId, count(r) as reportCount from UserReport r "
            + "where r.targetUserId in :targetUserIds group by r.targetUserId")
    List<TargetReportCount> countByTargetUserIds(@Param("targetUserIds") Collection<Long> targetUserIds);

    interface TargetReportCount {
        Long getTargetUserId();

        long getReportCount();
    }

    Page<UserReport> findAllBy(Pageable pageable);
}
