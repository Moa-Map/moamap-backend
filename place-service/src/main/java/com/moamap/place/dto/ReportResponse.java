package com.moamap.place.dto;

import com.moamap.place.entity.PlaceReport;
import java.time.LocalDateTime;

/** 신고 접수 결과. 앱은 "24시간 안에 검토할게요" 안내만 띄우면 된다. */
public record ReportResponse(Long reportId, LocalDateTime reportedAt) {

    public static ReportResponse from(PlaceReport report) {
        return new ReportResponse(report.getId(), report.getCreatedAt());
    }
}
