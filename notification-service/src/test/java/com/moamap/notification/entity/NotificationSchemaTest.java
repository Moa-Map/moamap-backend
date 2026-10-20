package com.moamap.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 운영 DB(PostgreSQL)에 만들어질 DDL을 확인한다. DB 없이 PostgreSQL 방언으로 스크립트만 뽑는다.
 */
@DataJpaTest
class NotificationSchemaTest {

    private static final Path DDL = createTempFile();

    @DynamicPropertySource
    static void postgresDdlScript(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action", () -> "create");
        registry.add("spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target", DDL::toString);
    }

    @Test
    void 알림_종류_컬럼에_허용_값_CHECK_제약을_만들지_않는다() throws IOException {
        // CHECK가 생기면 ddl-auto: update가 고치지 못해, 알림 종류를 추가했을 때 새 알림 저장이 거부된다.
        String ddl = Files.readString(DDL).toLowerCase();

        assertThat(ddl).contains("type varchar(40) not null", "target_type varchar(20) not null");
        assertThat(ddl).doesNotContain("check");
    }

    @Test
    void 같은_이벤트로_알림이_두_번_생기지_않게_유일_제약을_둔다() throws IOException {
        assertThat(Files.readString(DDL).toLowerCase())
                .contains("constraint uk_notification_source_event unique (source_event_id)");
    }

    private static Path createTempFile() {
        try {
            Path file = Files.createTempFile("notification-ddl", ".sql");
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
