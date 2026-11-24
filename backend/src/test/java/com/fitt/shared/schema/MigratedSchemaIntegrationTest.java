package com.fitt.shared.schema;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

// Starts the application the way production does: Flyway builds the schema from
// the migration files on a real MySQL, and Hibernate only validates it.
//
// Every other test lets Hibernate create the tables from the entities, so a
// migration that disagrees with its entity (a missing column, a wrong type)
// passes all of them and then stops the server on its next deploy. Here that
// disagreement fails the build instead: the context does not start.
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@ActiveProfiles("integration")
@Testcontainers
@Tag("integration")
class MigratedSchemaIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theMigrations_buildASchemaTheEntitiesAccept() {
        // Reaching this line is the test: validation ran while the context started.
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = 1", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(12);

        Integer mfaTables = jdbc.queryForObject("select count(*) from information_schema.tables"
                + " where table_schema = database()"
                + " and table_name in ('user_totp', 'mfa_recovery_codes', 'mfa_challenges')", Integer.class);
        assertThat(mfaTables).isEqualTo(3);

        Integer linkTables = jdbc.queryForObject("select count(*) from information_schema.tables"
                + " where table_schema = database() and table_name = 'device_link_codes'", Integer.class);
        assertThat(linkTables).isEqualTo(1);

        // V12's guard: open_owner_id is not mapped in the entity, and validate
        // accepts a column the entity does not name.
        Integer openOwnerKey = jdbc.queryForObject("select count(*) from information_schema.statistics"
                + " where table_schema = database() and table_name = 'workout_sessions'"
                + " and index_name = 'uk_workout_sessions_open_owner' and column_name = 'open_owner_id'"
                + " and non_unique = 0", Integer.class);
        assertThat(openOwnerKey).isEqualTo(1);
    }
}
