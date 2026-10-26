package com.moamap.map.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시각을 다루는 서비스가 테스트에서 시간을 고정할 수 있도록 Clock을 빈으로 등록한다.
 * (PublicRestroomSyncService의 syncStartedAt 고정 등)
 *
 * 반드시 Asia/Seoul을 명시한다 — 배포 이미지(eclipse-temurin:17-jre)의 기본 시간대는 UTC이고,
 * k8s 매니페스트에도 TZ를 맞춰주는 설정이 없다. systemDefaultZone()을 쓰면 실제 배포 환경에서
 * syncStartedAt/last_synced_at이 UTC로 저장돼 값만 봐서는 한국시간으로 착각하기 쉽다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
