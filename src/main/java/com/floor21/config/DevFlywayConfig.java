package com.floor21.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * After squashing migrations to {@code V1__baseline.sql}, local databases that still have
 * old Flyway history (checksum mismatch or removed V2–V50 entries) need a one-time repair.
 */
@Configuration
@Profile("dev")
public class DevFlywayConfig {

    @Bean
    public FlywayMigrationStrategy devFlywayMigrationStrategy() {
        return (Flyway flyway) -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
