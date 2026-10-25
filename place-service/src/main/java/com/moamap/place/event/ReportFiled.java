package com.moamap.place.event;

/**
 * 신고가 저장됐다는 프로세스 내부 신호. 커밋 후 ReportNotifier가 받아 알린다.
 *
 * @param reportId          신고 ID (운영자 알림이 가리킨다)
 * @param mapId             대상이 속한 지도
 * @param reporterId        신고자. 신고자가 방장·관리자여도 자기 신고 알림은 받지 않게 뺀다
 * @param reportedPlaceId   방장·관리자에게 알릴 장소. 장소 신고의 첫 신고일 때만 있고, 그 외에는 null
 */
public record ReportFiled(Long reportId, Long mapId, Long reporterId, Long reportedPlaceId) {

    public static ReportFiled toOperators(Long reportId, Long mapId, Long reporterId) {
        return new ReportFiled(reportId, mapId, reporterId, null);
    }

    public static ReportFiled toOperatorsAndManagers(Long reportId, Long mapId, Long reporterId, Long placeId) {
        return new ReportFiled(reportId, mapId, reporterId, placeId);
    }
}
