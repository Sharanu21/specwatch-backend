package com.specwatch.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Slf4j
@Configuration
public class DataSourceConfig {

    // Render-style vars
    @Value("${DB_URL:}")
    private String dbUrl;

    @Value("${DB_USERNAME:}")
    private String dbUsername;

    @Value("${DB_PASSWORD:}")
    private String dbPassword;

    // Replit / classic PG vars (fallback)
    @Value("${PGHOST:localhost}")
    private String pgHost;

    @Value("${PGPORT:5432}")
    private String pgPort;

    @Value("${PGDATABASE:specwatch}")
    private String pgDatabase;

    @Value("${PGUSER:postgres}")
    private String pgUser;

    @Value("${PGPASSWORD:}")
    private String pgPassword;

    @Primary
    @Bean
    public DataSource dataSource() {
        String jdbcUrl = resolveJdbcUrl();
        String user    = resolveUsername();
        String pass    = resolvePassword();

        log.info("DataSource: connecting to {}", jdbcUrl.replaceAll(":[^:@]+@", ":***@"));

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(user);
        ds.setPassword(pass);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(10);
        ds.setConnectionTimeout(30000);
        return ds;
    }

    private String resolveJdbcUrl() {
        if (!dbUrl.isBlank()) {
            // Convert postgres:// or postgresql:// → jdbc:postgresql://
            if (dbUrl.startsWith("postgres://")) {
                return dbUrl.replace("postgres://", "jdbc:postgresql://");
            }
            if (dbUrl.startsWith("postgresql://")) {
                return "jdbc:" + dbUrl;
            }
            // Already jdbc:postgresql:// format
            return dbUrl;
        }
        // Fall back to individual PG vars (Replit, local dev)
        return String.format("jdbc:postgresql://%s:%s/%s", pgHost, pgPort, pgDatabase);
    }

    private String resolveUsername() {
        return dbUsername.isBlank() ? pgUser : dbUsername;
    }

    private String resolvePassword() {
        return dbPassword.isBlank() ? pgPassword : dbPassword;
    }
}
