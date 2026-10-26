package com.moamap.user.auth.apple;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "apple.token")
public record AppleTokenProperties(@NotBlank String teamId, @NotBlank String keyId, @NotBlank String privateKey) {
    @Override
    public String toString() {
        return "AppleTokenProperties[redacted]";
    }
}
