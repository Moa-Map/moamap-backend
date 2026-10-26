package com.moamap.place.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services.user")
public record UserServiceProperties(String baseUrl) {
}
