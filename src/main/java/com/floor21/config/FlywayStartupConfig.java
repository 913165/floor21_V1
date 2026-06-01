package com.floor21.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Realigns {@code flyway_schema_history} checksums with classpath migrations before migrate.
 * Needed once after squashing V1–V50 into {@code V1__baseline.sql}; safe on every startup.
 */
@Configuration
public class FlywayStartupConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return (Flyway flyway) -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
