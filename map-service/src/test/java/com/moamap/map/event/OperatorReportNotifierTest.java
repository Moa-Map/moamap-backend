package com.moamap.map.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import com.moamap.map.user.UserAdminClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperatorReportNotifierTest {

    @Mock private UserAdminClient userAdminClient;
    @Mock private ActivityEventPublisher activityEventPublisher;
    @InjectMocks private OperatorReportNotifier notifier;

    @Test
    void 운영자마다_새_신고_알림을_보낸다_신고자는_싣지_않는다() {
        given(userAdminClient.findAdminIds()).willReturn(List.of(7L, 8L));

        notifier.on(new ReportFiled(30L, 5L));

        verify(activityEventPublisher).on(new ActivitySignal(ActivitySignal.REPORT_RECEIVED, 7L, null, 30L, 5L));
        verify(activityEventPublisher).on(new ActivitySignal(ActivitySignal.REPORT_RECEIVED, 8L, null, 30L, 5L));
    }

    @Test
    void 운영자를_가져오지_못하면_보내지_않는다() {
        given(userAdminClient.findAdminIds()).willReturn(List.of());

        notifier.on(new ReportFiled(30L, 5L));

        verify(activityEventPublisher, never()).on(any());
    }
}
