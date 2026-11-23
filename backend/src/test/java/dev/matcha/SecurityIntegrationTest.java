package dev.matcha;

import static org.junit.jupiter.api.Assertions.*;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import spark.Service;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class SecurityIntegrationTest {
    private static Database database;
    private static Service http;
    private static String schema, url, user, password, origin;
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static long account;

    @BeforeAll static void setup() throws Exception {
        url = System.getenv("TEST_DATABASE_URL"); user = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD");
        schema = "security_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) { statement.execute("CREATE SCHEMA " + schema); }
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        origin = "http://localhost:" + port;
        Config config = Config.from(Map.of("PORT", String.valueOf(port), "APP_URL", origin, "DATABASE_URL", url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, "DATABASE_USER", user, "DATABASE_PASSWORD", password));
        database = new Database(config); Migrations.apply(database);
        try (var connection = database.connection()) {
            account = Sql.number(connection, "INSERT INTO accounts(username,email,first_name,last_name,password_hash,verified) VALUES ('expiry_test','expiry@example.test','Expiry','Test',?,TRUE) RETURNING id", Passwords.hash("Violet!River42"));
            Sql.update(connection, "INSERT INTO profiles(user_id) VALUES (?)", account);
        }
        http = App.create(config, database);
    }

    @AfterAll static void cleanup() throws Exception {
        Live.shutdown();
        if (http != null) { http.stop(); http.awaitStop(); }
        if (database != null) database.close();
        if (schema != null) try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) { statement.execute("DROP SCHEMA " + schema + " CASCADE"); }
    }

    private static HttpResponse<String> post(String path, String json, String... headers) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(origin + path)).header("Content-Type", "application/json");
        for (int i = 0; i < headers.length; i += 2) builder.header(headers[i], headers[i + 1]);
        return CLIENT.send(builder.POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void expiredSingleUseLinksCannotChangeVerificationOrPassword() throws Exception {
        for (String purpose : new String[] {"verify", "reset"}) {
            String token = Secrets.token();
            try (var connection = database.connection()) {
                Sql.update(connection, "INSERT INTO account_tokens(token_hash,user_id,purpose,expires_at) VALUES (?,?,?,CURRENT_TIMESTAMP-INTERVAL '1 second')", Secrets.digest(token), account, purpose);
            }
            String path = purpose.equals("verify") ? "/api/auth/verify" : "/api/auth/reset-password";
            assertEquals(400, post(path, "{\"token\":\"" + token + "\",\"password\":\"Orchid!Cloud83\"}").statusCode());
        }
        try (var connection = database.connection()) {
            assertTrue(Passwords.verify("Violet!River42", (String) Sql.one(connection, "SELECT password_hash FROM accounts WHERE id=?", account).get("password_hash")));
        }
    }

    @Test void expiredSessionsAndMissingCsrfCannotWrite() throws Exception {
        String token = Secrets.token(), csrf = Secrets.token();
        try (var connection = database.connection()) {
            Sql.update(connection, "INSERT INTO sessions(token_hash,user_id,csrf_hash,expires_at) VALUES (?,?,?,CURRENT_TIMESTAMP-INTERVAL '1 second')", Secrets.digest(token), account, Secrets.digest(csrf));
        }
        assertEquals(401, post("/api/auth/logout", "{}", "Cookie", "matcha_session=" + token, "X-XSRF-TOKEN", csrf).statusCode());
        try (var connection = database.connection()) {
            Sql.update(connection, "UPDATE sessions SET expires_at=CURRENT_TIMESTAMP+INTERVAL '1 hour' WHERE token_hash=?", Secrets.digest(token));
        }
        assertEquals(403, post("/api/auth/logout", "{}", "Cookie", "matcha_session=" + token).statusCode());
        assertEquals(403, post("/api/auth/logout", "{}", "Cookie", "matcha_session=" + token, "X-XSRF-TOKEN", csrf, "Origin", "https://foreign.example").statusCode());
        assertEquals(200, post("/api/auth/logout", "{}", "Cookie", "matcha_session=" + token, "X-XSRF-TOKEN", csrf, "Origin", origin).statusCode());
        assertEquals(401, post("/api/auth/logout", "{}", "Cookie", "matcha_session=" + token, "X-XSRF-TOKEN", csrf).statusCode());
    }

    @Test void injectionAndWrongContentTypeCannotAuthenticate() throws Exception {
        var response = post("/api/auth/login", "{\"username\":\"' OR 1=1 --\",\"password\":\"Violet!River42\"}");
        assertEquals(401, response.statusCode());
        assertFalse(response.headers().firstValue("Set-Cookie").isPresent());
        var request = HttpRequest.newBuilder(URI.create(origin + "/api/auth/login")).header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        assertEquals(415, CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
