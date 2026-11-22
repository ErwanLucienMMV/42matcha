package dev.matcha;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public final class Chat {
    public void register(Api api) {
        api.get("/api/connections", true, context -> Sql.rows(context.connection(), """
            SELECT a.id,a.username,a.first_name,a.last_name,a.last_seen,
              (SELECT ph.id FROM photos ph WHERE ph.user_id=a.id AND ph.is_primary LIMIT 1) AS photo_id,
              (SELECT COUNT(*) FROM messages m WHERE m.sender_id=a.id AND m.recipient_id=? AND NOT m.is_read) AS unread_count,
              COALESCE((SELECT MAX(m.created_at) FROM messages m WHERE (m.sender_id=a.id AND m.recipient_id=l.actor_id) OR (m.recipient_id=a.id AND m.sender_id=l.actor_id)),l.created_at) AS updated_at
            FROM likes l JOIN likes r ON r.actor_id=l.target_id AND r.target_id=l.actor_id
              JOIN accounts a ON a.id=l.target_id
            WHERE l.actor_id=? AND NOT EXISTS(SELECT 1 FROM blocks b WHERE (b.actor_id=l.actor_id AND b.target_id=a.id) OR (b.target_id=l.actor_id AND b.actor_id=a.id))
            ORDER BY updated_at DESC,a.id
            """, context.userId(), context.userId()));
        api.get("/api/chat/:id", true, context -> {
            long partner = Input.id(context.request().params("id"));
            Relations.lock(context.connection(), context.userId(), partner);
            Relations.requireConnected(context.connection(), context.userId(), partner);
            String afterInput = context.request().queryParams("after");
            long boundary = boundary(afterInput == null ? context.request().queryParams("before") : afterInput, afterInput == null ? Long.MAX_VALUE : 0);
            boolean after = afterInput != null;
            String sql = "SELECT id,sender_id,recipient_id,body,is_read,created_at FROM messages WHERE ((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?)) AND id"
                    + (after ? ">? ORDER BY id ASC" : "<? ORDER BY id DESC") + " LIMIT 51";
            var rows = Sql.rows(context.connection(), sql, context.userId(), partner, partner, context.userId(), boundary);
            boolean more = rows.size() > 50;
            var messages = new ArrayList<>(rows.subList(0, Math.min(50, rows.size())));
            if (!after) Collections.reverse(messages);
            return Map.of("items", messages, "has_more", more);
        });
        api.post("/api/chat/:id", true, context -> {
            long partner = Input.id(context.request().params("id"));
            Relations.lock(context.connection(), context.userId(), partner);
            Relations.requireConnected(context.connection(), context.userId(), partner);
            String body = Input.text(Input.body(context.request()), "body", 1, 4000);
            var message = Sql.one(context.connection(), "INSERT INTO messages(sender_id,recipient_id,body) VALUES (?,?,?) RETURNING id,sender_id,recipient_id,body,is_read,created_at", context.userId(), partner, body);
            Notices.emit(context.connection(), partner, context.userId(), "message");
            context.response().status(201);
            return message;
        });
        api.post("/api/chat/:id/read", true, context -> {
            long partner = Input.id(context.request().params("id"));
            Relations.lock(context.connection(), context.userId(), partner);
            Relations.requireConnected(context.connection(), context.userId(), partner);
            var through = Input.body(context.request()).get("through");
            if (through == null || !through.isIntegralNumber() || !through.canConvertToLong() || through.asLong() < 0) throw ApiError.invalid("Provide a valid read boundary.");
            Sql.update(context.connection(), "UPDATE messages SET is_read=TRUE WHERE sender_id=? AND recipient_id=? AND id<=? AND NOT is_read", partner, context.userId(), through.asLong());
            return Map.of("message", "Messages marked as read.");
        });
        api.get("/api/updates", true, context -> Map.of(
                "unread_messages", Sql.number(context.connection(), """
                    SELECT COUNT(*) FROM messages m WHERE m.recipient_id=? AND NOT m.is_read
                      AND EXISTS(SELECT 1 FROM likes l WHERE l.actor_id=m.sender_id AND l.target_id=m.recipient_id)
                      AND EXISTS(SELECT 1 FROM likes l WHERE l.actor_id=m.recipient_id AND l.target_id=m.sender_id)
                      AND NOT EXISTS(SELECT 1 FROM blocks b WHERE (b.actor_id=m.sender_id AND b.target_id=m.recipient_id) OR (b.target_id=m.sender_id AND b.actor_id=m.recipient_id))
                    """, context.userId()),
                "unread_notifications", Sql.number(context.connection(), """
                    SELECT COUNT(*) FROM notifications n WHERE n.recipient_id=? AND NOT n.is_read
                      AND NOT EXISTS(SELECT 1 FROM blocks b WHERE (b.actor_id=n.actor_id AND b.target_id=n.recipient_id) OR (b.target_id=n.actor_id AND b.actor_id=n.recipient_id))
                      AND (n.kind='unlike' OR NOT EXISTS(SELECT 1 FROM notification_suppressions s WHERE s.recipient_id=n.recipient_id AND s.actor_id=n.actor_id))
                    """, context.userId())));
    }

    private static long boundary(String value, long fallback) {
        if (value == null) return fallback;
        try { long parsed = Long.parseLong(value); if (parsed < 0) throw new NumberFormatException(); return parsed; }
        catch (NumberFormatException error) { throw ApiError.invalid("Invalid message boundary."); }
    }
}
