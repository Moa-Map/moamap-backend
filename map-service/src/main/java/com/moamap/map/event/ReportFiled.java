package com.moamap.map.event;

/**
 * 신고가 저장됐다는 프로세스 내부 신호. 커밋 후 OperatorReportNotifier가 받아 운영자에게 알린다.
 */
public record ReportFiled(Long reportId, Long mapId) {
}
