package dev.matcha;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Locale;

public final class Accounts {
    private final Config config;
    private final Mail mail;
    private final RateLimit limits = new RateLimit();

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

    private static String password(JsonNode body) {
        JsonNode value = body.get("password");
        if (value == null || !value.isTextual() || value.asText().length() > 1000) throw ApiError.invalid("A password is required.");
        return value.asText();
    }
}
