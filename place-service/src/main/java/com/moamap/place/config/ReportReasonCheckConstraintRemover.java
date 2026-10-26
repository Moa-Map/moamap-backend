package com.moamap.place.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 댓글 신고 사유 컬럼에 남아 있는 허용 값 CHECK 제약을 지운다(PostgreSQL에서만, 한 번 지우면 이후엔 아무 일도 하지 않는다).
 *
 * 예전에는 사유를 @Enumerated로 저장해 Hibernate가 "reason in ('SPAM', ...)" 제약을 만들었다. ddl-auto: update는
 * 이 제약을 고치지 않아, 사유 PRIVACY를 추가하면 그 신고가 DB에서 거부된다. 지금은 컨버터로 저장하므로 새 DB에는
 * 제약이 생기지 않고, 이미 운영 중인 DB에만 남아 있는 제약을 여기서 정리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportReasonCheckConstraintRemover implements ApplicationRunner {

    /** PostgreSQL이 이름 없는 컬럼 CHECK에 붙이는 이름: 테이블_컬럼_check */
    static final String CONSTRAINT = "place_comment_reports_reason_check";

    /** 설정값을 SQL에 붙이므로 평범한 식별자만 허용한다. */
    private static final Pattern SCHEMA_NAME = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    private final DataSource dataSource;

    @Value("${spring.jpa.properties.hibernate.default_schema:}")
    private String schema;

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgres()) {
            return;
        }
        if (!schema.isBlank() && !SCHEMA_NAME.matcher(schema).matches()) {
            log.warn("스키마 이름 형식이 예상과 달라 댓글 신고 사유 제약 정리를 건너뛴다.");
            return;
        }
        String table = schema.isBlank() ? "place_comment_reports" : schema + ".place_comment_reports";
        try {
            new JdbcTemplate(dataSource).execute(
                "ALTER TABLE IF EXISTS " + table + " DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
        } catch (RuntimeException e) {
            // 정리에 실패해도 서비스는 뜬다. PRIVACY 사유 신고만 실패하므로 로그로 알린다.
            log.warn("댓글 신고 사유 CHECK 제약을 지우지 못했다. 새 사유(PRIVACY) 신고가 거부될 수 있다.", e);
        }
    }

    private boolean isPostgres() {
        try (Connection connection = dataSource.getConnection()) {
            return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException e) {
            return false;
        }
    }
}
