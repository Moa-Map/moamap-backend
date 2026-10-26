package com.moamap.place.map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services.map")
public record MapServiceProperties(String baseUrl) {
}
