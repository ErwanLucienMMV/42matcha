package dev.matcha;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small helpers around explicit SQL; no query generation or object mapping. */
public final class Sql {
    private static final ThreadLocal<List<Runnable>> AFTER_COMMIT = new ThreadLocal<>();
    private Sql() {}

    public static PreparedStatement statement(Connection connection, String sql, Object... values) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value instanceof Instant instant) value = Timestamp.from(instant);
                statement.setObject(i + 1, value);
            }
            return statement;
        } catch (SQLException | RuntimeException error) {
            statement.close();
            throw error;
        }
    }

    public static int update(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = statement(connection, sql, values)) { return statement.executeUpdate(); }
    }

    public static List<Map<String, Object>> rows(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = statement(connection, sql, values); var result = statement.executeQuery()) {
            var rows = new ArrayList<Map<String, Object>>();
            var metadata = result.getMetaData();
            while (result.next()) {
                var row = new LinkedHashMap<String, Object>();
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    Object value = result.getObject(i);
                    if (value instanceof Timestamp time) value = time.toInstant().toString();
                    if (value instanceof java.sql.Date date) value = date.toLocalDate().toString();
                    row.put(metadata.getColumnLabel(i), value);
                }
                rows.add(row);
            }
            return rows;
        }
    }

    public static Map<String, Object> one(Connection connection, String sql, Object... values) throws SQLException {
        var rows = rows(connection, sql, values);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public static long number(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = statement(connection, sql, values); ResultSet result = statement.executeQuery()) {
            if (!result.next()) throw new SQLException("Expected a scalar result");
            return result.getLong(1);
        }
    }

    @FunctionalInterface public interface Work<T> { T run(Connection connection) throws Exception; }

    public static void afterCommit(Runnable action) {
        var actions = AFTER_COMMIT.get();
        if (actions == null) action.run(); else actions.add(action);
    }

    public static <T> T transaction(Database database, Work<T> work) throws Exception {
        var actions = new ArrayList<Runnable>();
        AFTER_COMMIT.set(actions);
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                for (var action : actions) action.run();
                return result;
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        } finally { AFTER_COMMIT.remove(); }
    }
}
