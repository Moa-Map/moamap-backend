package com.moamap.map.dto;

import java.time.LocalDateTime;
import com.moamap.map.entity.MapContentReport;

/** 신고 접수 결과. 앱은 "24시간 안에 검토할게요" 안내만 띄우면 된다. */
public record ReportResponse(Long reportId, LocalDateTime reportedAt) {

    public static ReportResponse from(MapContentReport report) {
        return new ReportResponse(report.getId(), report.getCreatedAt());
    }
}
