package dev.matcha;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Profiles {
    private final Accounts accounts;
    public Profiles(Accounts accounts) { this.accounts = accounts; }

    public void register(Api api) {
        api.get("/api/me", true, context -> own(context.connection(), context.userId()));
        api.put("/api/me", true, context -> {
            var body = Input.body(context.request());
            String first = Input.text(body, "first_name", 1, 80);
            String last = Input.text(body, "last_name", 1, 80);
            String email = Input.email(body, "email");
            String gender = Input.choice(body, "gender", Set.of("woman", "man", "nonbinary"));
            String orientation = body.hasNonNull("orientation") ? Input.choice(body, "orientation", Set.of("bisexual", "heterosexual", "homosexual")) : "bisexual";
            String biography = Input.text(body, "biography", 1, 2000);
            var inputTags = body.get("tags");
            if (inputTags == null || !inputTags.isArray() || inputTags.size() < 1 || inputTags.size() > 10) throw ApiError.invalid("Choose between one and ten interest tags.");
            Set<String> tags = new LinkedHashSet<>();
            for (var tag : inputTags) {
                if (!tag.isTextual()) throw ApiError.invalid("Each interest must be a text tag.");
                String name = tag.asText().trim().toLowerCase(Locale.ROOT).replaceFirst("^#", "");
                if (!name.matches("[a-z0-9][a-z0-9_-]{0,39}")) throw ApiError.invalid("Tags use up to 40 letters, numbers, underscores, or hyphens.");
                tags.add(name);
            }
            Sql.number(context.connection(), "SELECT user_id FROM profiles WHERE user_id=? FOR UPDATE", context.userId());
            var account = Sql.one(context.connection(), "SELECT email FROM accounts WHERE id=?", context.userId());
            boolean changingEmail = !email.equals(account.get("email"));
            if (changingEmail) {
                if (Sql.number(context.connection(), "SELECT COUNT(*) FROM accounts WHERE LOWER(email)=? AND id<>?", email, context.userId()) != 0) throw new ApiError(409, "identity_taken", "That email is already in use.");
                accounts.sendToken(context.connection(), context.userId(), email, "email_change", email);
            }
            Sql.update(context.connection(), "UPDATE accounts SET first_name=?,last_name=? WHERE id=?", first, last, context.userId());
            Sql.update(context.connection(), "UPDATE profiles SET gender=?,orientation=?,biography=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", gender, orientation, biography, context.userId());
            Sql.update(context.connection(), "DELETE FROM profile_tags WHERE user_id=?", context.userId());
            for (String tag : tags) {
                Sql.update(context.connection(), "INSERT INTO tags(name) VALUES (?) ON CONFLICT(name) DO NOTHING", tag);
                Sql.update(context.connection(), "INSERT INTO profile_tags(user_id,tag_id) SELECT ?,id FROM tags WHERE name=?", context.userId(), tag);
            }
            return Map.of("message", changingEmail ? "Profile saved. Verify the link sent to your new email to finish that change." : "Your profile is saved.", "email_change_pending", changingEmail);
        });
        api.get("/api/tags", true, context -> {
            String query = context.request().queryParams("q");
            if (query == null) query = "";
            if (query.length() > 40) throw ApiError.invalid("The tag query is too long.");
            return Sql.rows(context.connection(), "SELECT name FROM tags WHERE name LIKE ? ORDER BY name LIMIT 30", query.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "") + "%");
        });
    }

    public static Map<String, Object> own(Connection connection, long id) throws SQLException {
        var row = Sql.one(connection, "SELECT a.id,a.username,a.email,a.first_name,a.last_name,p.gender,p.orientation,p.biography,p.birth_date,p.city,p.latitude,p.longitude,p.gps_consent FROM accounts a JOIN profiles p ON p.user_id=a.id WHERE a.id=?", id);
        if (row == null) throw ApiError.missing();
        row.put("tags", Sql.rows(connection, "SELECT t.name FROM tags t JOIN profile_tags pt ON pt.tag_id=t.id WHERE pt.user_id=? ORDER BY t.name", id).stream().map(tag -> tag.get("name")).toList());
        row.put("photos", Sql.rows(connection, "SELECT id,is_primary FROM photos WHERE user_id=? ORDER BY is_primary DESC,id", id));
        row.put("popularity", Preferences.popularity(connection, id));
        row.put("age", row.get("birth_date") == null ? null : java.time.Period.between(java.time.LocalDate.parse((String) row.get("birth_date")), java.time.LocalDate.now(java.time.ZoneOffset.UTC)).getYears());
        return row;
    }
}
