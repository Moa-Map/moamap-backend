package com.moamap.user.report.dto;

import com.moamap.user.report.entity.UserReport;
import java.time.Instant;

/** 신고 접수 결과. 앱은 "24시간 안에 검토할게요" 안내만 띄우면 된다. */
public record ReportResponse(Long reportId, Instant reportedAt) {

    public static ReportResponse from(UserReport report) {
        return new ReportResponse(report.getId(), report.getCreatedAt());
    }
}
