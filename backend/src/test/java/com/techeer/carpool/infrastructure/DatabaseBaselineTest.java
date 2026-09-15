package com.techeer.carpool.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DatabaseBaselineTest {
    @Container
    static final PostgreSQLContainer database = new PostgreSQLContainer(DockerImageName.parse(
            System.getenv().getOrDefault("MOA_POSTGIS_IMAGE", "postgis/postgis:15-3.5"))
            .asCompatibleSubstituteFor("postgres"));

    @Test void baselineIsRepeatableWithoutLosingExistingRows() throws Exception {
        Flyway flyway = Flyway.configure().dataSource(database.getJdbcUrl(),database.getUsername(),database.getPassword())
                .locations("classpath:db/migration").target("1").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        try(var connection=java.sql.DriverManager.getConnection(database.getJdbcUrl(),database.getUsername(),database.getPassword());
            var statement=connection.createStatement()) {
            statement.executeUpdate("INSERT INTO tags(name,created_at,updated_at) VALUES('baseline',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try(var rows=statement.executeQuery("SELECT count(*) FROM tags WHERE name='baseline'")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }
}
