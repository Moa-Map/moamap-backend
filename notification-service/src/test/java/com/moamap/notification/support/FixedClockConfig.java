package com.moamap.notification.support;

import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class FixedClockConfig {

    @Bean
    public Clock clock() {
        return NotificationFixtures.FIXED_CLOCK;
    }
}
