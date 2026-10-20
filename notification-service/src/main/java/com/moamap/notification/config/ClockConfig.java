package com.moamap.notification.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 읽음 시각·보관 기간 계산이 테스트에서 시간을 고정할 수 있도록 Clock을 빈으로 둔다.
 * 시각은 모두 Instant(UTC)로 저장하므로 시간대는 결과에 영향이 없다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
