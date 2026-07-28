package com.dipendra.test.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class MysqlMigrationTests {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("migration_test")
            .withUsername("migration_user")
            .withPassword("migration_password");

    @Test
    void migratesCleanMysqlAndCreatesQualificationTables() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();

        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                var statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name IN (?,?,?,?)")) {
            statement.setString(1, MYSQL.getDatabaseName());
            statement.setString(2, "paper_trade");
            statement.setString(3, "qualification_run");
            statement.setString(4, "reliability_incident");
            statement.setString(5, "paper_daily_result");
            try (var result = statement.executeQuery()) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(4);
            }
        }
    }
}
