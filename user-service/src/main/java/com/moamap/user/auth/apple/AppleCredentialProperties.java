package com.moamap.user.auth.apple;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "apple.credentials")
public record AppleCredentialProperties(@NotBlank String activeKeyVersion,
                                        @NotEmpty Map<String, String> keys) {
    @Override
    public String toString() {
        return "AppleCredentialProperties[redacted]";
    }
}
