package com.moamap.map.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseTimeEntity의 생성/수정 시각 자동 기록을 위한 JPA Auditing 활성화.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
