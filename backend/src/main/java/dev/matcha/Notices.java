package dev.matcha;

import java.sql.Connection;
import java.sql.SQLException;

public final class Notices {
    private Notices() {}

    public static void emit(Connection connection, long recipient, long actor, String kind) throws SQLException {
        if (recipient == actor || Relations.blocked(connection, recipient, actor)) return;
        if (!kind.equals("unlike") && Sql.number(connection, "SELECT COUNT(*) FROM notification_suppressions WHERE recipient_id=? AND actor_id=?", recipient, actor) > 0) return;
        Sql.update(connection, "INSERT INTO notifications(recipient_id,actor_id,kind) VALUES (?,?,?)", recipient, actor, kind);
        Sql.afterCommit(() -> Live.publish(recipient));
    }
}
