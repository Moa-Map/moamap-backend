package com.moamap.place.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini.api")
public record GeminiProperties(String key, String model) {
}
