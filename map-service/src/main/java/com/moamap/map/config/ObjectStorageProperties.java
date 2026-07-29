package com.moamap.map.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 오브젝트 스토리지 접속 설정. 자격증명은 절대 커밋하지 않고 환경변수로만 주입한다(application.yml의 storage.* 참고).
 */
@ConfigurationProperties(prefix = "storage")
public record ObjectStorageProperties(
    String endpoint,
    String region,
    String bucket,
    String publicBaseUrl,
    String accessKey,
    String secretKey
) {
}
