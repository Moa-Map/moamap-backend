package com.moamap.map.event;

import com.moamap.map.user.UserAdminClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 새 신고를 운영자(관리자 계정) 모두에게 알린다.
 *
 * 커밋 후에 운영자 목록을 가져온다. 트랜잭션 안에서 user-service를 부르면 그동안 DB 커넥션을 잡고 있게 된다.
 * 이 시점에는 트랜잭션이 끝났으므로 ActivityEventPublisher를 이벤트가 아니라 직접 호출해 바로 보낸다.
 */
@Component
@RequiredArgsConstructor
public class OperatorReportNotifier {

    private final UserAdminClient userAdminClient;
    private final ActivityEventPublisher activityEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ReportFiled reportFiled) {
        for (Long adminId : userAdminClient.findAdminIds()) {
            activityEventPublisher.on(new ActivitySignal(ActivitySignal.REPORT_RECEIVED, adminId, null,
                reportFiled.reportId(), reportFiled.mapId()));
        }
    }
}
