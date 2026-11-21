package dev.matcha;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class Database implements AutoCloseable {
    private final HikariDataSource pool;

    public Database(Config config) {
        HikariConfig settings = new HikariConfig();
        settings.setJdbcUrl(config.databaseUrl());
        settings.setUsername(config.databaseUser());
        settings.setPassword(config.databasePassword());
        settings.setMaximumPoolSize(12);
        settings.setMinimumIdle(2);
        settings.setConnectionTimeout(5000);
        settings.setPoolName("matcha");
        pool = new HikariDataSource(settings);
    }

    public Connection connection() throws SQLException { return pool.getConnection(); }

    public boolean healthy() {
        try (Connection connection = connection()) {
            return connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    @Override public void close() { pool.close(); }
}
