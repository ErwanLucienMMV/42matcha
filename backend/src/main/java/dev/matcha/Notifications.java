package dev.matcha;

import java.util.ArrayList;
import java.util.Map;

public final class Notifications {
    public void register(Api api) {
        api.post("/api/notifications/read-all", true, context -> {
            var through = Input.body(context.request()).get("through");
            if (through == null || !through.isIntegralNumber() || !through.canConvertToLong() || through.asLong() < 1) throw ApiError.invalid("Provide the last displayed notification identifier.");
            Sql.update(context.connection(), "UPDATE notifications SET is_read=TRUE WHERE recipient_id=? AND id<=? AND NOT is_read", context.userId(), through.asLong());
            Sql.afterCommit(() -> Live.publish(context.userId()));
            return Map.of("message", "Displayed notifications marked as read.");
        });
        api.get("/api/notifications", true, context -> {
            String before = context.request().queryParams("before");
            long boundary = before == null ? Long.MAX_VALUE : Input.id(before);
            var rows = Sql.rows(context.connection(), """
                SELECT n.id,n.actor_id,n.kind,n.is_read,n.created_at,a.first_name,a.last_name,a.username
                FROM notifications n JOIN accounts a ON a.id=n.actor_id
                WHERE n.recipient_id=? AND n.id<?
                  AND NOT EXISTS(SELECT 1 FROM blocks b WHERE (b.actor_id=n.actor_id AND b.target_id=n.recipient_id) OR (b.target_id=n.actor_id AND b.actor_id=n.recipient_id))
                  AND (n.kind='unlike' OR NOT EXISTS(SELECT 1 FROM notification_suppressions s WHERE s.recipient_id=n.recipient_id AND s.actor_id=n.actor_id))
                ORDER BY n.id DESC LIMIT 51
                """, context.userId(), boundary);
            return Map.of("items", new ArrayList<>(rows.subList(0, Math.min(50, rows.size()))), "has_more", rows.size() > 50);
        });
        api.post("/api/notifications/:id/read", true, context -> {
            long id = Input.id(context.request().params("id"));
            int changed = Sql.update(context.connection(), "UPDATE notifications SET is_read=TRUE WHERE id=? AND recipient_id=?", id, context.userId());
            if (changed == 0) throw ApiError.missing();
            Sql.afterCommit(() -> Live.publish(context.userId()));
            return Map.of("message", "Notification marked as read.");
        });
    }
}
