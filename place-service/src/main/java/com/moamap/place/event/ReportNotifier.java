package com.moamap.place.event;

import com.moamap.place.map.MapClient;
import com.moamap.place.user.UserAdminClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 신고를 알린다. 운영자(관리자 계정)에게는 모든 신고를, 지도의 방장·관리자에게는 장소의 첫 신고를 알린다.
 *
 * 커밋 후에 받을 사람을 가져온다. 트랜잭션 안에서 다른 서비스를 부르면 그동안 DB 커넥션을 잡고 있게 된다.
 * 이 시점에는 트랜잭션이 끝났으므로 ActivityEventPublisher를 이벤트가 아니라 직접 호출해 바로 보낸다.
 * 신고자는 알림에 싣지 않는다(방장·관리자에게 신고자를 보여주지 않는다).
 */
@Component
@RequiredArgsConstructor
public class ReportNotifier {

    private final UserAdminClient userAdminClient;
    private final MapClient mapClient;
    private final ActivityEventPublisher activityEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ReportFiled report) {
        for (Long adminId : userAdminClient.findAdminIds()) {
            activityEventPublisher.on(new ActivitySignal(ActivitySignal.REPORT_RECEIVED, adminId, null,
                report.reportId(), report.mapId()));
        }
        if (report.reportedPlaceId() == null) {
            return;
        }
        for (Long managerId : mapClient.findManagerIds(report.mapId())) {
            if (!managerId.equals(report.reporterId())) {
                activityEventPublisher.on(new ActivitySignal(ActivitySignal.PLACE_REPORTED, managerId, null,
                    report.reportedPlaceId(), report.mapId()));
            }
        }
    }
}
