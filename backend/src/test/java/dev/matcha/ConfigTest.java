package dev.matcha;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigTest {
    private Map<String, String> environment() {
        return new HashMap<>(Map.of("DATABASE_URL", "jdbc:postgresql://localhost/matcha",
                "DATABASE_USER", "matcha", "DATABASE_PASSWORD", "test-only"));
    }

    @Test void missingDatabaseCredentialsFailBeforeStartup() {
        assertThrows(IllegalArgumentException.class, () -> Config.from(Map.of()));
    }

    @Test void rejectsInvalidPortsAndNonOriginAppUrls() {
        var env = environment();
        env.put("PORT", "65536");
        assertThrows(IllegalArgumentException.class, () -> Config.from(env));
        env.remove("PORT");
        env.put("APP_URL", "http://localhost:8080/path");
        assertThrows(IllegalArgumentException.class, () -> Config.from(env));
    }

    @Test void validConfigurationKeepsSecretsOutOfOrigin() {
        Config config = Config.from(environment());
        assertEquals(8080, config.port());
        assertEquals("http://localhost:8080", config.appUrl());
        assertFalse(config.secureCookies());
    }
}
