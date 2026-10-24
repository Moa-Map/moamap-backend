package com.moamap.place.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

class ReportReasonCheckConstraintRemoverTest {

    @Test
    void PostgreSQL이_아니면_아무것도_하지_않는다() throws Exception {
        DataSource dataSource = dataSourceOf("H2");
        ReportReasonCheckConstraintRemover remover = new ReportReasonCheckConstraintRemover(dataSource);
        ReflectionTestUtils.setField(remover, "schema", "place_service");

        remover.run(new DefaultApplicationArguments());

        // 제품 이름 확인에만 커넥션을 한 번 쓰고, 제약을 지우는 SQL은 실행하지 않는다.
        verify(dataSource).getConnection();
    }

    @Test
    void 스키마_이름_형식이_이상하면_SQL을_실행하지_않는다() throws Exception {
        DataSource dataSource = dataSourceOf("PostgreSQL");
        ReportReasonCheckConstraintRemover remover = new ReportReasonCheckConstraintRemover(dataSource);
        ReflectionTestUtils.setField(remover, "schema", "place_service; drop table x");

        remover.run(new DefaultApplicationArguments());

        verify(dataSource).getConnection();
    }

    private static DataSource dataSourceOf(String productName) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.getMetaData()).willReturn(metaData);
        given(metaData.getDatabaseProductName()).willReturn(productName);
        return dataSource;
    }
}
