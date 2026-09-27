package com.moamap.place.config;

import com.moamap.common.storage.ObjectStoragePresigner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * AWS S3 연동 설정. 버킷/리전은 환경변수로 주입되며 이 클래스에는 값을 하드코딩하지 않는다
 * (application.yml의 storage.* 참고).
 *
 * 자격증명은 DefaultCredentialsProvider가 찾는다 — 운영(EC2)은 노드 인스턴스 프로파일,
 * 로컬은 ~/.aws/credentials 또는 AWS_* 환경변수. 액세스 키를 애플리케이션 설정으로 받지 않는다.
 */
@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageConfig {

    @Bean
    public S3Presigner s3Presigner(ObjectStorageProperties properties) {
        return S3Presigner.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(DefaultCredentialsProvider.builder().build())
            .build();
    }

    @Bean
    public ObjectStoragePresigner objectStoragePresigner(S3Presigner s3Presigner, ObjectStorageProperties properties) {
        return new ObjectStoragePresigner(s3Presigner, properties.bucket(), properties.publicBaseUrl());
    }
}
