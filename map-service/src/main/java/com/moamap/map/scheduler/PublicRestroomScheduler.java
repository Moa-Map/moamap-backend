package com.moamap.map.scheduler;

import com.moamap.map.service.PublicRestroomSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublicRestroomScheduler {

    private final PublicRestroomSyncService syncService;

    // zone 명시 필수 — 배포 이미지 기본 시간대가 UTC라, zone 없이는 "새벽 4시"가 실제로 낮 1시(KST)에 돈다.
    @Scheduled(cron = "${map.restroom.sync-cron:0 0 4 1 * *}", zone = "Asia/Seoul")
    public void syncPublicRestrooms() {
        syncService.syncAll();
    }
}
