package dev.matcha;

import java.util.Map;

public final class Moderation {
    public void register(Api api) {
        api.post("/api/people/:id/block", true, context -> {
            long target = Input.id(context.request().params("id"));
            Relations.lock(context.connection(), context.userId(), target);
            if (Sql.number(context.connection(), "SELECT COUNT(*) FROM accounts WHERE id=? AND verified", target) == 0) throw ApiError.missing();
            Sql.update(context.connection(), "INSERT INTO blocks(actor_id,target_id) VALUES (?,?) ON CONFLICT DO NOTHING", context.userId(), target);
            Sql.update(context.connection(), "DELETE FROM likes WHERE (actor_id=? AND target_id=?) OR (actor_id=? AND target_id=?)", context.userId(), target, target, context.userId());
            Sql.update(context.connection(), "UPDATE notifications SET is_read=TRUE WHERE (recipient_id=? AND actor_id=?) OR (recipient_id=? AND actor_id=?)", context.userId(), target, target, context.userId());
            Sql.update(context.connection(), "UPDATE messages SET is_read=TRUE WHERE (sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?)", context.userId(), target, target, context.userId());
            Sql.afterCommit(() -> { Live.publish(context.userId()); Live.publish(target); });
            return Map.of("message", "User blocked. They will no longer appear in discovery, chat or notifications.");
        });
        api.delete("/api/people/:id/block", true, context -> {
            long target = Input.id(context.request().params("id"));
            Relations.lock(context.connection(), context.userId(), target);
            Sql.update(context.connection(), "DELETE FROM blocks WHERE actor_id=? AND target_id=?", context.userId(), target);
            return Map.of("message", "User unblocked. Previous likes have not been restored.");
        });
        api.get("/api/me/blocks", true, context -> Sql.rows(context.connection(), "SELECT a.id,a.username,a.first_name,a.last_name,b.created_at FROM blocks b JOIN accounts a ON a.id=b.target_id WHERE b.actor_id=? ORDER BY b.created_at DESC", context.userId()));
        api.post("/api/people/:id/report", true, context -> {
            long target = Input.id(context.request().params("id"));
            Relations.lock(context.connection(), context.userId(), target);
            if (Sql.number(context.connection(), "SELECT COUNT(*) FROM accounts WHERE id=? AND verified", target) == 0) throw ApiError.missing();
            String reason = Input.text(Input.body(context.request()), "reason", 3, 1000);
            Sql.update(context.connection(), "INSERT INTO reports(reporter_id,target_id,reason) VALUES (?,?,?) ON CONFLICT(reporter_id,target_id) DO NOTHING", context.userId(), target, reason);
            return Map.of("message", "Your fake-account report has been recorded.");
        });
    }
}
