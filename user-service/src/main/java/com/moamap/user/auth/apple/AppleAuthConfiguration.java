package com.moamap.user.auth.apple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moamap.user.user.repository.UserRepository;
import com.moamap.user.auth.service.AppleLoginService;
import com.moamap.user.auth.service.AuthService;
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
@EnableConfigurationProperties({AppleProperties.class, AppleTokenProperties.class, AppleCredentialProperties.class})
public class AppleAuthConfiguration {
    @Bean
    AppleNonceService appleNonceService(StringRedisTemplate redis) {
        return new AppleNonceService(redis);
    }

    @Bean
    ApplePublicKeyProvider applePublicKeyProvider(RestClient.Builder builder) {
        return new ApplePublicKeyProvider(appleHttpClient(builder), Clock.systemUTC());
    }

    @Bean
    AppleIdentityTokenVerifier appleIdentityTokenVerifier(AppleProperties properties,
            ApplePublicKeyProvider keys, AppleNonceService nonces) {
        return new AppleIdentityTokenVerifier(properties, keys, nonces, Clock.systemUTC());
    }

    @Bean
    AppleClientSecretProvider appleClientSecretProvider(AppleProperties apple, AppleTokenProperties token) {
        return new AppleClientSecretProvider(apple, token, Clock.systemUTC());
    }

    @Bean
    AppleTokenExchanger appleTokenExchanger(RestClient.Builder builder, AppleProperties properties,
            AppleClientSecretProvider secrets, AppleIdentityTokenVerifier verifier, ObjectMapper mapper) {
        return new AppleTokenExchanger(appleHttpClient(builder), properties, secrets, verifier, mapper);
    }

    @Bean
    AppleTokenCipher appleTokenCipher(AppleCredentialProperties properties) {
        return new AppleTokenCipher(properties);
    }

    @Bean
    AppleCredentialStore appleCredentialStore(UserRepository users, AppleCredentialRepository credentials,
            AppleTokenCipher cipher, AppleProperties properties) {
        return new AppleCredentialStore(users, credentials, cipher, properties);
    }

    @Bean
    AppleLoginService appleLoginService(AppleIdentityTokenVerifier verifier, AppleTokenExchanger exchanger,
            AppleCredentialStore credentials, AuthService authService) {
        return new AppleLoginService(verifier, exchanger, credentials, authService);
    }

    private RestClient appleHttpClient(RestClient.Builder builder) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        return builder.clone().requestFactory(factory).build();
    }
}
