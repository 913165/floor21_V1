package com.floor21.config;

import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.Flyway;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Applies pending Flyway scripts as soon as the primary {@link DataSource} is ready, before JPA opens a
 * connection for {@code ddl-auto: validate}.
 */
@Configuration(proxyBeanMethods = false)
public class FlywayDataSourceMigrationConfig {

    private static final Logger log = LogManager.getLogger(FlywayDataSourceMigrationConfig.class);

    @Bean
    static BeanPostProcessor flywayMigrateOnDataSourceReady(
            ObjectProvider<FlywayMigrationStrategy> migrationStrategy) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (!(bean instanceof DataSource dataSource) || !"dataSource".equals(beanName)) {
                    return bean;
                }
                Flyway flyway =
                        Flyway.configure()
                                .dataSource(dataSource)
                                .locations("classpath:db/migration")
                                .load();
                FlywayMigrationStrategy strategy = migrationStrategy.getIfAvailable();
                if (strategy != null) {
                    strategy.migrate(flyway);
                    log.info("Flyway early migrate on dataSource completed (custom strategy)");
                } else {
                    flyway.repair();
                    var result = flyway.migrate();
                    log.info(
                            "Flyway early migrate on dataSource: {} migration(s) applied",
                            result.migrationsExecuted);
                }
                ensureUsersCompanyNameColumn(dataSource);
                ensureBuildingsUpdatedAtColumn(dataSource);
                ensureParkingFloorConfigColumn(dataSource);
                ensureFlatsLinkedResidentialColumn(dataSource);
                ensureFlatsAreaBreakdownColumns(dataSource);
                return bean;
            }
        };
    }

    /** Idempotent guard when V2 is recorded in history but the column was never applied. */
    private static void ensureUsersCompanyNameColumn(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS company_name VARCHAR(200)");
        } catch (Exception ex) {
            throw new IllegalStateException("Could not ensure users.company_name column exists", ex);
        }
    }

    /** Idempotent guard when V4 is recorded in history but {@code updated_at} was never applied. */
    private static void ensureBuildingsUpdatedAtColumn(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE buildings ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP");
            statement.execute(
                    "UPDATE buildings SET updated_at = COALESCE(created_at, NOW()) WHERE updated_at IS NULL");
            statement.execute("ALTER TABLE buildings ALTER COLUMN updated_at SET DEFAULT NOW()");
            statement.execute("UPDATE buildings SET updated_at = NOW() WHERE updated_at IS NULL");
            statement.execute("ALTER TABLE buildings ALTER COLUMN updated_at SET NOT NULL");
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not ensure buildings.updated_at column exists", ex);
        }
    }

    /** Idempotent guard when V5 is recorded in history but the column was never applied. */
    private static void ensureParkingFloorConfigColumn(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE buildings ADD COLUMN IF NOT EXISTS parking_floor_config TEXT");
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not ensure buildings.parking_floor_config column exists", ex);
        }
    }

    /** Idempotent guard when V6 is recorded in history but the column was never applied. */
    private static void ensureFlatsLinkedResidentialColumn(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "ALTER TABLE flats ADD COLUMN IF NOT EXISTS linked_residential_flat_id UUID");
            statement.execute(
                    """
                    DO $$
                    BEGIN
                      IF NOT EXISTS (
                        SELECT 1 FROM pg_constraint WHERE conname = 'flats_linked_residential_flat_id_fkey'
                      ) THEN
                        ALTER TABLE flats
                          ADD CONSTRAINT flats_linked_residential_flat_id_fkey
                          FOREIGN KEY (linked_residential_flat_id) REFERENCES flats (id) ON DELETE SET NULL;
                      END IF;
                    END $$""");
            statement.execute(
                    """
                    CREATE INDEX IF NOT EXISTS idx_flats_linked_residential_flat_id
                      ON flats (linked_residential_flat_id)""");
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not ensure flats.linked_residential_flat_id column exists", ex);
        }
    }

    /** Idempotent guard when V7 is recorded in history but area breakdown columns were never applied. */
    private static void ensureFlatsAreaBreakdownColumns(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE flats ADD COLUMN IF NOT EXISTS carpet_area_sqft DECIMAL(10, 2)");
            statement.execute("ALTER TABLE flats ADD COLUMN IF NOT EXISTS balcony_area_sqft DECIMAL(10, 2)");
            statement.execute(
                    "ALTER TABLE flats ADD COLUMN IF NOT EXISTS pre_merge_carpet_area_sqft DECIMAL(10, 2)");
            statement.execute(
                    "ALTER TABLE flats ADD COLUMN IF NOT EXISTS pre_merge_balcony_area_sqft DECIMAL(10, 2)");
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not ensure flats carpet/balcony area columns exist", ex);
        }
    }
}
