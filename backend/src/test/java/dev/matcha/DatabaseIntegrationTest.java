package dev.matcha;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class DatabaseIntegrationTest {
    private static Database database;
    private static String schema;
    private static String url;
    private static String user;
    private static String password;

    @BeforeAll static void setup() throws Exception {
        url = System.getenv("TEST_DATABASE_URL");
        user = System.getenv("TEST_DATABASE_USER");
        password = System.getenv("TEST_DATABASE_PASSWORD");
        schema = "test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        database = new Database(Config.from(Map.of("DATABASE_URL", url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                "DATABASE_USER", user, "DATABASE_PASSWORD", password)));
        Migrations.apply(database);
    }

    @AfterAll static void cleanup() throws Exception {
        if (database != null) database.close();
        if (schema != null) try (Connection connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test void migrationsAreIdempotent() throws Exception {
        Migrations.apply(database);
        try (Connection connection = database.connection()) {
            assertEquals(1, Sql.number(connection, "SELECT COUNT(*) FROM schema_migrations"));
        }
    }

    @Test void failedTransactionsLeaveNoPartialWrites() throws Exception {
        String username = "rollback_" + UUID.randomUUID().toString().substring(0, 8);
        assertThrows(IllegalStateException.class, () -> Sql.transaction(database, connection -> {
            Sql.update(connection, "INSERT INTO accounts (username,email,first_name,last_name,password_hash) VALUES (?,?, 'Test','Person','test-only')", username, username + "@example.test");
            throw new IllegalStateException("Deliberate transaction failure");
        }));
        try (Connection connection = database.connection()) {
            assertEquals(0, Sql.number(connection, "SELECT COUNT(*) FROM accounts WHERE username = ?", username));
        }
    }

    @Test void databaseEnforcesCaseInsensitiveIdentityAndPhotoLimit() throws Exception {
        Sql.transaction(database, connection -> {
            long id = Sql.number(connection, "INSERT INTO accounts (username,email,first_name,last_name,password_hash) VALUES ('Photo_Test','photo@example.test','Photo','Tester','test-only') RETURNING id");
            Sql.update(connection, "INSERT INTO profiles(user_id) VALUES (?)", id);
            for (int i = 0; i < 5; i++) Sql.update(connection, "INSERT INTO photos (user_id,content,media_type,is_primary) VALUES (?,?,'image/png',?)", id, new byte[]{1}, i == 0);
            return null;
        });
        try (Connection connection = database.connection()) {
            long id = Sql.number(connection, "SELECT id FROM accounts WHERE username = 'Photo_Test'");
            assertThrows(java.sql.SQLException.class, () -> Sql.update(connection, "INSERT INTO photos (user_id,content,media_type) VALUES (?,?,'image/png')", id, new byte[]{1}));
            assertThrows(java.sql.SQLException.class, () -> Sql.update(connection, "INSERT INTO accounts (username,email,first_name,last_name,password_hash) VALUES ('photo_test','other@example.test','Test','Person','test-only')"));
            assertThrows(java.sql.SQLException.class, () -> Sql.update(connection, "INSERT INTO likes(actor_id,target_id) VALUES (?,?)", id, id));
        }
    }
}
