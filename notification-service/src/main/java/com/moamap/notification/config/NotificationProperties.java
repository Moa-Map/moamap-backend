package com.moamap.notification.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param retentionDays 받은 지 이 기간(일)이 지난 알림은 정리 배치가 지운다
 */
@Validated
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(@Positive int retentionDays) {
}
