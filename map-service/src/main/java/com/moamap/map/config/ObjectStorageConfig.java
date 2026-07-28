package com.moamap.map.config;

import java.net.URI;
import com.moamap.common.storage.ObjectStoragePresigner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * NHN Cloud Object Storage(S3 호환) 연동 설정. 자격증명/버킷/엔드포인트는 전부 환경변수로 주입되며
 * 이 클래스에는 값을 하드코딩하지 않는다(application.yml의 storage.* 참고).
 *
 * endpoint가 비어 있으면 빈을 만들지 않는다. 스토리지 설정 없이 띄우는 로컬·테스트 환경에서
 * URI 파싱 실패로 애플리케이션 기동이 막히는 것을 피하기 위함이다.
 */
@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
@ConditionalOnProperty(prefix = "storage", name = "endpoint")
public class ObjectStorageConfig {

    @Bean
    public S3Presigner s3Presigner(ObjectStorageProperties properties) {
        return S3Presigner.builder()
            .region(Region.of(properties.region()))
            .endpointOverride(URI.create(properties.endpoint()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
            .build();
    }

    @Bean
    public ObjectStoragePresigner objectStoragePresigner(S3Presigner s3Presigner, ObjectStorageProperties properties) {
        return new ObjectStoragePresigner(s3Presigner, properties.bucket(), properties.publicBaseUrl());
    }
}
