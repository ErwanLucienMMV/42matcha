package dev.matcha;

import java.util.Map;

public record Config(int port, String databaseUrl, String databaseUser,
                     String databasePassword, String appUrl, boolean secureCookies,
                     String smtpHost, int smtpPort, String mailFrom, int registrationLimit) {
    public static Config from(Map<String, String> env) {
        String origin = env.getOrDefault("APP_URL", "http://localhost:8080");
        if (!origin.matches("https?://[^/]+")) {
            throw new IllegalArgumentException("APP_URL must be an HTTP(S) origin without a trailing slash");
        }
        return new Config(port(env, "PORT", 8080), required(env, "DATABASE_URL"),
                required(env, "DATABASE_USER"), required(env, "DATABASE_PASSWORD"),
                origin, Boolean.parseBoolean(env.getOrDefault("COOKIE_SECURE", "false")),
                env.getOrDefault("SMTP_HOST", "localhost"), port(env, "SMTP_PORT", 1025),
                env.getOrDefault("MAIL_FROM", "Matcha <hello@matcha.local>"), port(env, "REGISTRATION_LIMIT", 30));
    }

    @Override public String toString() {
        return "Config[port=" + port + ", appUrl=" + appUrl + ", secureCookies=" + secureCookies + ", databaseCredentials=REDACTED]";
    }

    private static String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private static int port(Map<String, String> env, String key, int fallback) {
        try {
            int value = Integer.parseInt(env.getOrDefault(key, String.valueOf(fallback)));
            if (value < 1 || value > 65535) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a valid port", e);
        }
    }
}
