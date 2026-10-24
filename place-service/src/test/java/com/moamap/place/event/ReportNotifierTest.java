package com.moamap.place.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.moamap.place.map.MapClient;
import com.moamap.place.user.UserAdminClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportNotifierTest {

    private static final Long REPORT_ID = 30L;
    private static final Long MAP_ID = 10L;
    private static final Long PLACE_ID = 7L;
    private static final Long REPORTER = 2L;

    @Mock private UserAdminClient userAdminClient;
    @Mock private MapClient mapClient;
    @Mock private ActivityEventPublisher activityEventPublisher;
    @InjectMocks private ReportNotifier notifier;

    @Test
    void 운영자에게는_신고를_방장_관리자에게는_장소를_알리고_신고자는_싣지_않는다() {
        given(userAdminClient.findAdminIds()).willReturn(List.of(100L));
        given(mapClient.findManagerIds(MAP_ID)).willReturn(List.of(1L, 4L));

        notifier.on(ReportFiled.toOperatorsAndManagers(REPORT_ID, MAP_ID, REPORTER, PLACE_ID));

        verify(activityEventPublisher).on(new ActivitySignal(ActivitySignal.REPORT_RECEIVED, 100L, null, REPORT_ID, MAP_ID));
        verify(activityEventPublisher).on(new ActivitySignal(ActivitySignal.PLACE_REPORTED, 1L, null, PLACE_ID, MAP_ID));
        verify(activityEventPublisher).on(new ActivitySignal(ActivitySignal.PLACE_REPORTED, 4L, null, PLACE_ID, MAP_ID));
    }

    @Test
    void 신고자가_방장_관리자여도_자기_신고_알림은_받지_않는다() {
        given(userAdminClient.findAdminIds()).willReturn(List.of());
        given(mapClient.findManagerIds(MAP_ID)).willReturn(List.of(REPORTER));

        notifier.on(ReportFiled.toOperatorsAndManagers(REPORT_ID, MAP_ID, REPORTER, PLACE_ID));

        verify(activityEventPublisher, never()).on(any());
    }

    @Test
    void 운영자에게만_알리는_신고는_방장_관리자를_조회하지_않는다() {
        given(userAdminClient.findAdminIds()).willReturn(List.of(100L));

        notifier.on(ReportFiled.toOperators(REPORT_ID, MAP_ID, REPORTER));

        verify(activityEventPublisher).on(new ActivitySignal(ActivitySignal.REPORT_RECEIVED, 100L, null, REPORT_ID, MAP_ID));
        verifyNoInteractions(mapClient);
    }
}
