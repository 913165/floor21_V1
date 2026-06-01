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
}
