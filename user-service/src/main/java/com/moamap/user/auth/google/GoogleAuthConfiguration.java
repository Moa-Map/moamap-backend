package com.moamap.user.auth.google;

import com.moamap.user.auth.service.AuthService;
import com.moamap.user.auth.service.GoogleLoginService;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "google", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(GoogleProperties.class)
public class GoogleAuthConfiguration {
    @Bean
    GooglePublicKeyProvider googlePublicKeyProvider(RestClient.Builder builder) {
        return new GooglePublicKeyProvider(googleHttpClient(builder), Clock.systemUTC());
    }

    @Bean
    GoogleIdTokenVerifier googleIdTokenVerifier(GoogleProperties properties, GooglePublicKeyProvider keys) {
        return new GoogleIdTokenVerifier(properties, keys, Clock.systemUTC());
    }

    @Bean
    GoogleLoginService googleLoginService(GoogleIdTokenVerifier verifier, AuthService authService) {
        return new GoogleLoginService(verifier, authService);
    }

    private RestClient googleHttpClient(RestClient.Builder builder) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        return builder.clone().requestFactory(factory).build();
    }
}
