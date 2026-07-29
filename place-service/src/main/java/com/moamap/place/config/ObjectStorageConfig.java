package com.moamap.place.config;

import java.net.URI;
import com.moamap.common.storage.ObjectStoragePresigner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * NHN Cloud Object Storage(S3 호환) 연동 설정. 자격증명/버킷/엔드포인트는 전부 환경변수로 주입되며
 * 이 클래스에는 값을 하드코딩하지 않는다(application.yml의 storage.* 참고).
 */
@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageConfig {

    @Bean
    public S3Presigner s3Presigner(ObjectStorageProperties properties) {
        return S3Presigner.builder()
            .region(Region.of(properties.region()))
            .endpointOverride(URI.create(properties.endpoint()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
            // endpoint 자체가 /v1/AUTH_xxx 경로를 포함하는 NHN Cloud 특성상, path-style을 강제하지 않으면
            // SDK가 virtual-hosted-style URL을 만들어 그 경로가 object key에 잘못 섞여 들어간다.
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build())
            .build();
    }

    @Bean
    public ObjectStoragePresigner objectStoragePresigner(S3Presigner s3Presigner, ObjectStorageProperties properties) {
        return new ObjectStoragePresigner(s3Presigner, properties.bucket(), properties.publicBaseUrl());
    }
}
