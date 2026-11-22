package dev.matcha;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Locale;
import java.util.Collections;
import java.util.LinkedHashMap;
import spark.Request;
import spark.Response;

public final class Accounts {
    private final Config config;
    private final Mail mail;
    private final RateLimit limits = new RateLimit();
    private static final String DUMMY_HASH = Passwords.hash(Secrets.token());

    public Accounts(Config config) {
        this.config = config;
        mail = new Mail(config);
    }

    public void registration(Api api) {
        api.post("/api/auth/register", false, context -> {
            limits.check("register:" + context.request().ip(), 30, 3600);
            JsonNode body = Input.body(context.request());
            String username = Input.text(body, "username", 3, 30);
            if (!username.matches("[A-Za-z0-9_]{3,30}")) throw ApiError.invalid("Use 3-30 letters, numbers, or underscores for your username.");
            String email = Input.email(body, "email");
            String first = Input.text(body, "first_name", 1, 80);
            String last = Input.text(body, "last_name", 1, 80);
            String password = password(body);
            Passwords.validate(password);
            try {
                long id = Sql.number(context.connection(), "INSERT INTO accounts(username,email,first_name,last_name,password_hash) VALUES (?,?,?,?,?) RETURNING id", username, email, first, last, Passwords.hash(password));
                Sql.update(context.connection(), "INSERT INTO profiles(user_id) VALUES (?)", id);
                sendToken(context.connection(), id, email, "verify", null);
            } catch (SQLException error) {
                if ("23505".equals(error.getSQLState())) throw new ApiError(409, "identity_taken", "That username or email is already in use.");
                throw error;
            }
            context.response().status(201);
            return Map.of("message", "Check your email for a link to verify your account.");
        });
        api.post("/api/auth/verify", false, context -> {
            limits.check("verify:" + context.request().ip(), 100, 900);
            String token = Input.text(Input.body(context.request()), "token", 43, 43);
            var row = Sql.one(context.connection(), "DELETE FROM account_tokens WHERE token_hash=? AND purpose IN ('verify','email_change') AND expires_at>CURRENT_TIMESTAMP RETURNING user_id,purpose,new_email", Secrets.digest(token));
            if (row == null) throw ApiError.invalid("This verification link has expired or was already used.");
            long id = ((Number) row.get("user_id")).longValue();
            if ("email_change".equals(row.get("purpose"))) {
                try { Sql.update(context.connection(), "UPDATE accounts SET email=?,verified=TRUE WHERE id=?", row.get("new_email"), id); }
                catch (SQLException error) {
                    if ("23505".equals(error.getSQLState())) throw new ApiError(409, "identity_taken", "That email is already in use.");
                    throw error;
                }
            } else Sql.update(context.connection(), "UPDATE accounts SET verified=TRUE WHERE id=?", id);
            return Map.of("message", "Your email is verified. You can sign in now.");
        });
    }

    public void sendToken(Connection connection, long id, String email, String purpose, String newEmail) throws SQLException {
        String raw = Secrets.token();
        Sql.update(connection, "DELETE FROM account_tokens WHERE user_id=? AND purpose=?", id, purpose);
        Sql.update(connection, "INSERT INTO account_tokens(token_hash,user_id,purpose,new_email,expires_at) VALUES (?,?,?,?,?)",
                Secrets.digest(raw), id, purpose, newEmail, Instant.now().plus(purpose.equals("reset") ? 30 : 1440, ChronoUnit.MINUTES));
        String path = purpose.equals("reset") ? "/reset-password" : "/verify";
        String subject = purpose.equals("reset") ? "Reset your Matcha password" : "Verify your Matcha email";
        mail.send(email, subject, "Hello,\n\n" + subject + " using this single-use link:\n\n" + config.appUrl() + path + "?token=" + raw + "\n\nIf you did not request this, you can ignore this email.\n\nMatcha");
    }

    public void authentication(Api api) {
        api.authentication(this::authenticate);
        api.get("/api/auth/session", false, context -> {
            var row = session(context.request(), context.connection());
            if (row != null) {
                row = new LinkedHashMap<>(row);
                row.remove("csrf_hash");
            }
            return Collections.singletonMap("user", row);
        });
        api.post("/api/auth/login", false, context -> {
            limits.check("login:" + context.request().ip(), 100, 900);
            var body = Input.body(context.request());
            String username = Input.text(body, "username", 3, 30);
            String password = password(body);
            var row = Sql.one(context.connection(), "SELECT id,password_hash,verified FROM accounts WHERE LOWER(username)=LOWER(?)", username);
            if (!Passwords.verify(password, row == null ? DUMMY_HASH : (String) row.get("password_hash")) || row == null) {
                throw new ApiError(401, "invalid_credentials", "The username or password is incorrect.");
            }
            if (!Boolean.TRUE.equals(row.get("verified"))) throw ApiError.forbidden("Verify your email before signing in.");
            long id = ((Number) row.get("id")).longValue();
            String token = Secrets.token();
            String csrf = Secrets.token();
            Sql.update(context.connection(), "DELETE FROM sessions WHERE expires_at<CURRENT_TIMESTAMP");
            Sql.update(context.connection(), "INSERT INTO sessions(token_hash,user_id,csrf_hash,expires_at) VALUES (?,?,?,?)", Secrets.digest(token), id, Secrets.digest(csrf), Instant.now().plus(7, ChronoUnit.DAYS));
            Sql.update(context.connection(), "UPDATE accounts SET last_seen=CURRENT_TIMESTAMP WHERE id=?", id);
            cookie(context.response(), "matcha_session", token, 604800, true);
            cookie(context.response(), "XSRF-TOKEN", csrf, 604800, false);
            return Map.of("message", "Signed in.");
        });
        api.post("/api/auth/logout", true, context -> {
            Sql.update(context.connection(), "DELETE FROM sessions WHERE token_hash=?", Secrets.digest(context.request().cookie("matcha_session")));
            String tokenHash = Secrets.digest(context.request().cookie("matcha_session"));
            Sql.afterCommit(() -> Live.revokeToken(tokenHash));
            cookie(context.response(), "matcha_session", "", 0, true);
            cookie(context.response(), "XSRF-TOKEN", "", 0, false);
            return Map.of("message", "Signed out.");
        });
        api.post("/api/auth/forgot-password", false, context -> {
            String email = Input.email(Input.body(context.request()), "email");
            limits.check("reset-ip:" + context.request().ip(), 50, 3600);
            limits.check("reset-email:" + Secrets.digest(email), 5, 900);
            var account = Sql.one(context.connection(), "SELECT id FROM accounts WHERE LOWER(email)=? AND verified", email);
            if (account != null) sendToken(context.connection(), ((Number) account.get("id")).longValue(), email, "reset", null);
            return Map.of("message", "If a verified account uses that email, a reset link is on its way.");
        });
        api.post("/api/auth/reset-password", false, context -> {
            limits.check("reset-use:" + context.request().ip(), 50, 900);
            var body = Input.body(context.request());
            String token = Input.text(body, "token", 43, 43);
            String password = password(body);
            Passwords.validate(password);
            var account = Sql.one(context.connection(), "DELETE FROM account_tokens WHERE token_hash=? AND purpose='reset' AND expires_at>CURRENT_TIMESTAMP RETURNING user_id", Secrets.digest(token));
            if (account == null) throw ApiError.invalid("This reset link has expired or was already used.");
            long id = ((Number) account.get("user_id")).longValue();
            Sql.update(context.connection(), "UPDATE accounts SET password_hash=? WHERE id=?", Passwords.hash(password), id);
            Sql.update(context.connection(), "DELETE FROM sessions WHERE user_id=?", id);
            Sql.afterCommit(() -> Live.revokeUser(id));
            return Map.of("message", "Your password is updated. Please sign in again.");
        });
        api.post("/api/auth/resend-verification", false, context -> {
            String email = Input.email(Input.body(context.request()), "email");
            limits.check("resend-ip:" + context.request().ip(), 50, 3600);
            limits.check("resend-email:" + Secrets.digest(email), 5, 900);
            var account = Sql.one(context.connection(), "SELECT id FROM accounts WHERE LOWER(email)=? AND NOT verified", email);
            if (account != null) sendToken(context.connection(), ((Number) account.get("id")).longValue(), email, "verify", null);
            return Map.of("message", "If an unverified account uses that email, a new link is on its way.");
        });
    }

    public long authenticate(Request request, Connection connection, boolean write) throws SQLException {
        var row = session(request, connection);
        if (row == null) throw new ApiError(401, "authentication_required", "Please sign in.");
        if (write && !Secrets.matches(request.headers("X-XSRF-TOKEN"), (String) row.get("csrf_hash"))) {
            throw ApiError.forbidden("Your security token is missing or expired. Refresh the page and try again.");
        }
        return ((Number) row.get("id")).longValue();
    }

    private Map<String, Object> session(Request request, Connection connection) throws SQLException {
        String token = request.cookie("matcha_session");
        if (token == null || token.length() != 43) return null;
        var row = Sql.one(connection, "SELECT a.id,a.username,a.email,a.first_name,a.last_name,a.verified,s.csrf_hash FROM sessions s JOIN accounts a ON a.id=s.user_id WHERE s.token_hash=? AND s.expires_at>CURRENT_TIMESTAMP AND a.verified", Secrets.digest(token));
        if (row != null) {
            Sql.update(connection, "UPDATE sessions SET last_seen=CURRENT_TIMESTAMP WHERE token_hash=?", Secrets.digest(token));
            Sql.update(connection, "UPDATE accounts SET last_seen=CURRENT_TIMESTAMP WHERE id=?", row.get("id"));
        }
        return row;
    }

    private void cookie(Response response, String name, String value, int age, boolean httpOnly) {
        response.raw().addHeader("Set-Cookie", name + "=" + value + "; Path=/; Max-Age=" + age + "; SameSite=Lax" + (httpOnly ? "; HttpOnly" : "") + (config.secureCookies() ? "; Secure" : ""));
    }

    private static String password(JsonNode body) {
        JsonNode value = body.get("password");
        if (value == null || !value.isTextual() || value.asText().length() > 1000) throw ApiError.invalid("A password is required.");
        return value.asText();
    }
}
