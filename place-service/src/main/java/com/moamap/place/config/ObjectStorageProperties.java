package com.moamap.place.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
