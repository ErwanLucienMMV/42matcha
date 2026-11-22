package dev.matcha;

import java.sql.Connection;
import java.sql.SQLException;

public final class Relations {
    private Relations() {}

    public static void lock(Connection connection, long actor, long target) throws SQLException {
        if (actor == target) throw ApiError.invalid("Choose another user's profile.");
        String pair = Math.min(actor, target) + ":" + Math.max(actor, target);
        try (var statement = Sql.statement(connection, "SELECT pg_advisory_xact_lock(hashtextextended(?,0))", pair)) { statement.execute(); }
    }

    public static boolean blocked(Connection connection, long actor, long target) throws SQLException {
        return Sql.number(connection, "SELECT COUNT(*) FROM blocks WHERE (actor_id=? AND target_id=?) OR (actor_id=? AND target_id=?)", actor, target, target, actor) > 0;
    }

    public static void requireVisible(Connection connection, long actor, long target) throws SQLException {
        if (Sql.number(connection, "SELECT COUNT(*) FROM accounts WHERE id=? AND verified", target) == 0 || blocked(connection, actor, target)) throw ApiError.missing();
    }

    public static boolean connected(Connection connection, long actor, long target) throws SQLException {
        return !blocked(connection, actor, target) && Sql.number(connection, "SELECT COUNT(*) FROM likes WHERE (actor_id=? AND target_id=?) OR (actor_id=? AND target_id=?)", actor, target, target, actor) == 2;
    }

    public static void requireConnected(Connection connection, long actor, long target) throws SQLException {
        if (!connected(connection, actor, target)) throw ApiError.forbidden("Chat is available when you both like each other and neither has blocked the other.");
    }
}
