package com.moamap.map.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AWS S3 접속 설정. 자격증명은 설정으로 받지 않는다 — DefaultCredentialsProvider가 찾는다(ObjectStorageConfig 참고).
 */
@ConfigurationProperties(prefix = "storage")
public record ObjectStorageProperties(
    String region,
    String bucket,
    String publicBaseUrl
) {
}
