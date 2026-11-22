package dev.matcha;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;

public final class Interactions {
    public void register(Api api) {
        api.get("/api/people/:id", true, context -> profile(context.connection(), context.userId(), Input.id(context.request().params("id"))));
        api.post("/api/people/:id/visit", true, context -> {
            long target = Input.id(context.request().params("id"));
            Relations.lock(context.connection(), context.userId(), target);
            Relations.requireVisible(context.connection(), context.userId(), target);
            Sql.update(context.connection(), "INSERT INTO visits(visitor_id,target_id) VALUES (?,?) ON CONFLICT(visitor_id,target_id) DO UPDATE SET visited_at=CURRENT_TIMESTAMP", context.userId(), target);
            Notices.emit(context.connection(), target, context.userId(), "visit");
            return Map.of("message", "Visit recorded.");
        });
        api.post("/api/people/:id/like", true, context -> {
            long target = Input.id(context.request().params("id"));
            Relations.lock(context.connection(), context.userId(), target);
            Relations.requireVisible(context.connection(), context.userId(), target);
            if (Sql.number(context.connection(), "SELECT COUNT(*) FROM photos WHERE user_id=? AND is_primary", context.userId()) == 0) throw ApiError.forbidden("Add a profile photo before sending likes.");
            if (Sql.number(context.connection(), "SELECT COUNT(*) FROM photos WHERE user_id=? AND is_primary", target) == 0) throw ApiError.forbidden("This user has not added a profile photo yet.");
            int added = Sql.update(context.connection(), "INSERT INTO likes(actor_id,target_id) VALUES (?,?) ON CONFLICT(actor_id,target_id) DO NOTHING", context.userId(), target);
            Sql.update(context.connection(), "DELETE FROM notification_suppressions WHERE recipient_id=? AND actor_id=?", context.userId(), target);
            boolean connected = Relations.connected(context.connection(), context.userId(), target);
            if (added > 0) {
                if (connected) {
                    Sql.update(context.connection(), "DELETE FROM notification_suppressions WHERE (recipient_id=? AND actor_id=?) OR (recipient_id=? AND actor_id=?)", context.userId(), target, target, context.userId());
                    Notices.emit(context.connection(), target, context.userId(), "connection");
                    Notices.emit(context.connection(), context.userId(), target, "connection");
                } else Notices.emit(context.connection(), target, context.userId(), "like");
            }
            return Map.of("connected", connected, "message", connected ? "The feeling is mutual. You can say hello." : "Like sent.");
        });
        api.delete("/api/people/:id/like", true, context -> {
            long target = Input.id(context.request().params("id"));
            Relations.lock(context.connection(), context.userId(), target);
            Relations.requireVisible(context.connection(), context.userId(), target);
            boolean connected = Relations.connected(context.connection(), context.userId(), target);
            int removed = Sql.update(context.connection(), "DELETE FROM likes WHERE actor_id=? AND target_id=?", context.userId(), target);
            if (removed > 0) {
                if (connected) Notices.emit(context.connection(), target, context.userId(), "unlike");
                Sql.update(context.connection(), "INSERT INTO notification_suppressions(recipient_id,actor_id) VALUES (?,?) ON CONFLICT DO NOTHING", context.userId(), target);
            }
            return Map.of("message", "Like removed. Chat is no longer available.");
        });
        api.get("/api/me/visitors", true, context -> Sql.rows(context.connection(), "SELECT a.id,a.username,a.first_name,a.last_name,v.visited_at FROM visits v JOIN accounts a ON a.id=v.visitor_id WHERE v.target_id=? AND NOT EXISTS(SELECT 1 FROM blocks b WHERE (b.actor_id=? AND b.target_id=a.id) OR (b.actor_id=a.id AND b.target_id=?)) ORDER BY v.visited_at DESC,a.id", context.userId(), context.userId(), context.userId()));
        api.get("/api/me/likes", true, context -> Sql.rows(context.connection(), "SELECT a.id,a.username,a.first_name,a.last_name,l.created_at FROM likes l JOIN accounts a ON a.id=l.actor_id WHERE l.target_id=? AND NOT EXISTS(SELECT 1 FROM blocks b WHERE (b.actor_id=? AND b.target_id=a.id) OR (b.actor_id=a.id AND b.target_id=?)) ORDER BY l.created_at DESC,a.id", context.userId(), context.userId(), context.userId()));
    }

    public static Map<String, Object> profile(Connection connection, long viewer, long target) throws SQLException {
        Relations.requireVisible(connection, viewer, target);
        var row = Sql.one(connection, "SELECT a.id,a.username,a.first_name,a.last_name,a.last_seen,p.gender,p.orientation,p.biography,p.city,EXTRACT(YEAR FROM AGE(p.birth_date))::INTEGER AS age FROM accounts a JOIN profiles p ON p.user_id=a.id WHERE a.id=?", target);
        if (row == null) throw ApiError.missing();
        row.put("photos", Sql.rows(connection, "SELECT id,is_primary FROM photos WHERE user_id=? ORDER BY is_primary DESC,id", target));
        row.put("tags", Sql.rows(connection, "SELECT t.name FROM tags t JOIN profile_tags pt ON pt.tag_id=t.id WHERE pt.user_id=? ORDER BY t.name", target).stream().map(tag -> tag.get("name")).toList());
        row.put("popularity", Preferences.popularity(connection, target));
        row.put("i_like", Sql.number(connection, "SELECT COUNT(*) FROM likes WHERE actor_id=? AND target_id=?", viewer, target) > 0);
        row.put("likes_me", Sql.number(connection, "SELECT COUNT(*) FROM likes WHERE actor_id=? AND target_id=?", target, viewer) > 0);
        row.put("connected", Relations.connected(connection, viewer, target));
        row.put("own_photo", Sql.number(connection, "SELECT COUNT(*) FROM photos WHERE user_id=? AND is_primary", viewer) > 0);
        row.put("online", row.get("last_seen") != null && Instant.parse((String) row.get("last_seen")).isAfter(Instant.now().minusSeconds(45)));
        return row;
    }
}
