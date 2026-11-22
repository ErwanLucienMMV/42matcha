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
            assertEquals(2, Sql.number(connection, "SELECT COUNT(*) FROM schema_migrations"));
        }
    }

    @Test void liveActionsRunOnlyAfterSuccessfulCommit() throws Exception {
        var committed = new java.util.concurrent.atomic.AtomicBoolean();
        var rolledBack = new java.util.concurrent.atomic.AtomicBoolean();
        Sql.transaction(database, connection -> { Sql.afterCommit(() -> committed.set(true)); assertFalse(committed.get()); return null; });
        assertTrue(committed.get());
        assertThrows(IllegalStateException.class, () -> Sql.transaction(database, connection -> {
            Sql.afterCommit(() -> rolledBack.set(true)); throw new IllegalStateException("Deliberate rollback");
        }));
        assertFalse(rolledBack.get());
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

    @Test void popularityCountsDistinctVisitorsAndCurrentLikes() throws Exception {
        Sql.transaction(database, connection -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            long target = Sql.number(connection, "INSERT INTO accounts(username,email,first_name,last_name,password_hash) VALUES (?,?,'Score','Target','test-only') RETURNING id", "score_" + suffix, "score_" + suffix + "@example.test");
            for (int i = 0; i < 21; i++) {
                String name = "visitor_" + suffix + "_" + i;
                long visitor = Sql.number(connection, "INSERT INTO accounts(username,email,first_name,last_name,password_hash) VALUES (?,?,'Score','Visitor','test-only') RETURNING id", name, name + "@example.test");
                Sql.update(connection, "INSERT INTO likes(actor_id,target_id) VALUES (?,?)", visitor, target);
                if (i == 0) {
                    Sql.update(connection, "INSERT INTO visits(visitor_id,target_id) VALUES (?,?) ON CONFLICT(visitor_id,target_id) DO UPDATE SET visited_at=CURRENT_TIMESTAMP", visitor, target);
                    Sql.update(connection, "INSERT INTO visits(visitor_id,target_id) VALUES (?,?) ON CONFLICT(visitor_id,target_id) DO UPDATE SET visited_at=CURRENT_TIMESTAMP", visitor, target);
                    assertEquals(6, Preferences.popularity(connection, target));
                }
            }
            assertEquals(100, Preferences.popularity(connection, target));
            Sql.update(connection, "DELETE FROM likes WHERE target_id=?", target);
            assertEquals(1, Preferences.popularity(connection, target));
            return null;
        });
    }

    @Test void suggestionsApplyMutualPreferencesGeographySortingAndBlocks() throws Exception {
        Sql.transaction(database, connection -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            long me = candidate(connection, "me_" + suffix, "woman", "heterosexual", 48.86, 2.35);
            long near = candidate(connection, "near_" + suffix, "man", "heterosexual", 48.87, 2.35);
            long far = candidate(connection, "far_" + suffix, "man", "bisexual", 45.75, 4.85);
            candidate(connection, "excluded_" + suffix, "woman", "heterosexual", 48.86, 2.35);
            candidate(connection, "opposite_" + suffix, "man", "homosexual", 48.86, 2.35);
            Sql.update(connection, "INSERT INTO likes(actor_id,target_id) VALUES (?,?)", me, far);
            var results = Matching.find(connection, me, Matching.Filter.defaults(), true);
            assertEquals(near, ((Number) results.items().getFirst().get("id")).longValue());
            assertEquals(2, results.items().size());
            var popular = new Matching.Filter(18,120,0,100,"",20050,0,"popularity","desc",1,20);
            assertEquals(far, ((Number) Matching.find(connection,me,popular,true).items().getFirst().get("id")).longValue());
            Sql.update(connection, "INSERT INTO blocks(actor_id,target_id) VALUES (?,?)", near, me);
            var unblocked = Matching.find(connection, me, Matching.Filter.defaults(), true).items();
            assertEquals(1, unblocked.size());
            assertEquals(far, ((Number) unblocked.getFirst().get("id")).longValue());
            assertFalse(unblocked.getFirst().containsKey("email"));
            assertFalse(unblocked.getFirst().containsKey("latitude"));
            var search = Matching.find(connection, me, Matching.Filter.defaults(), false, java.util.List.of("hiking"));
            assertTrue(search.items().size() > unblocked.size());
            assertTrue(Matching.find(connection, me, Matching.Filter.defaults(), false, java.util.List.of("hiking", "not-shared")).items().isEmpty());
            return null;
        });
    }

    private static long candidate(Connection connection, String name, String gender, String orientation, double latitude, double longitude) throws Exception {
        long id = Sql.number(connection, "INSERT INTO accounts(username,email,first_name,last_name,password_hash,verified) VALUES (?,?,'Test','Candidate','test-only',TRUE) RETURNING id", name, name + "@example.test");
        Sql.update(connection, "INSERT INTO profiles(user_id,gender,orientation,birth_date,biography,city,latitude,longitude) VALUES (?,?,?,DATE '1996-01-01','A complete test profile','Test city',?,?)", id, gender, orientation, latitude, longitude);
        Sql.update(connection, "INSERT INTO tags(name) VALUES ('hiking') ON CONFLICT(name) DO NOTHING");
        Sql.update(connection, "INSERT INTO profile_tags(user_id,tag_id) SELECT ?,id FROM tags WHERE name='hiking'", id);
        return id;
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
