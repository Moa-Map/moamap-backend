package com.moamap.map.scheduler;

import com.moamap.map.service.FootTrafficCongestionSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FootTrafficCongestionScheduler {

    private final FootTrafficCongestionSyncService syncService;

    @Scheduled(fixedRateString = "${map.congestion.sync-interval-ms:300000}")
    public void syncFootTrafficCongestion() {
        syncService.syncAll();
    }
}
