package dev.matcha;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

public final class Migrations {
    private Migrations() {}

    public static void apply(Database database) throws Exception {
        Sql.transaction(database, connection -> {
            try (var lock = connection.createStatement()) { lock.execute("SELECT pg_advisory_xact_lock(426242)"); }
            Sql.update(connection, "CREATE TABLE IF NOT EXISTS schema_migrations (version VARCHAR(120) PRIMARY KEY, checksum CHAR(64) NOT NULL, applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            for (String file : List.of("V001__initial_schema.sql")) {
                byte[] bytes;
                try (var resource = Migrations.class.getResourceAsStream("/db/" + file)) {
                    if (resource == null) throw new IllegalStateException("Missing migration: " + file);
                    bytes = resource.readAllBytes();
                }
                String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                var applied = Sql.one(connection, "SELECT checksum FROM schema_migrations WHERE version = ?", file);
                if (applied != null) {
                    if (!checksum.equals(applied.get("checksum"))) throw new IllegalStateException("Applied migration changed: " + file);
                    continue;
                }
                try (var statement = connection.createStatement()) { statement.execute(new String(bytes, StandardCharsets.UTF_8)); }
                Sql.update(connection, "INSERT INTO schema_migrations (version, checksum) VALUES (?, ?)", file, checksum);
            }
            return null;
        });
    }
}
