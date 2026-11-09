package com.restorant;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in integration check against the configured MySQL database, without changing its schema. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class DatabaseConnectionIT {

    @Autowired
    private DataSource dataSource;

    @Test
    void applicationStartsAndConnectsToMySql() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT 1")) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
    }
}
