package com.moamap.user.auth.apple;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "apple", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AppleProperties.class)
public class AppleAuthConfiguration {
    @Bean
    AppleNonceService appleNonceService(StringRedisTemplate redis) {
        return new AppleNonceService(redis);
    }

    @Bean
    ApplePublicKeyProvider applePublicKeyProvider(RestClient.Builder builder) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        return new ApplePublicKeyProvider(builder.clone().requestFactory(factory).build(), Clock.systemUTC());
    }

    @Bean
    AppleIdentityTokenVerifier appleIdentityTokenVerifier(AppleProperties properties,
            ApplePublicKeyProvider keys, AppleNonceService nonces) {
        return new AppleIdentityTokenVerifier(properties, keys, nonces, Clock.systemUTC());
    }
}
