package dev.matcha;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

@WebSocket(maxTextMessageSize = 256, maxBinaryMessageSize = 256)
public final class Live implements AutoCloseable {
    private record Client(long userId, String tokenHash, AtomicBoolean pending, AtomicLong acknowledgedAt) {}
    private static volatile Live active;
    private final Database database;
    private final Config config;
    private final ConcurrentHashMap<Session, Client> clients = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "matcha-live-heartbeat"); thread.setDaemon(true); return thread;
    });

    public Live(Database database, Config config) {
        this.database = database; this.config = config; active = this;
        heartbeat.scheduleWithFixedDelay(this::heartbeat, 5, 5, TimeUnit.SECONDS);
    }

    @OnWebSocketConnect public void connected(Session session) {
        if (!config.appUrl().equals(session.getUpgradeRequest().getOrigin())) { session.close(1008, "Invalid origin"); return; }
        String token = session.getUpgradeRequest().getCookies().stream().filter(cookie -> cookie.getName().equals("matcha_session")).map(java.net.HttpCookie::getValue).findFirst().orElse("");
        if (token.length() != 43) { session.close(1008, "Sign in required"); return; }
        String hash = Secrets.digest(token);
        try (Connection connection = database.connection()) {
            var row = Sql.one(connection, "SELECT s.user_id FROM sessions s JOIN accounts a ON a.id=s.user_id WHERE s.token_hash=? AND s.expires_at>CURRENT_TIMESTAMP AND a.verified", hash);
            if (row == null) { session.close(1008, "Session expired"); return; }
            long id = ((Number) row.get("user_id")).longValue();
            synchronized (clients) {
                if (clients.size() >= 512 || clients.values().stream().filter(client -> client.userId() == id).count() >= 8) { session.close(1008, "Connection limit"); return; }
                clients.put(session, new Client(id, hash, new AtomicBoolean(), new AtomicLong(System.currentTimeMillis())));
            }
            session.setIdleTimeout(45000);
            Sql.update(connection, "UPDATE accounts SET last_seen=CURRENT_TIMESTAMP WHERE id=?", id);
            send(session, "ready");
        } catch (SQLException error) { session.close(1013, "Live updates unavailable"); }
    }

    @OnWebSocketClose public void closed(Session session, int status, String reason) {
        Client client = clients.remove(session);
        if (client != null) try (Connection connection = database.connection()) {
            Sql.update(connection, "UPDATE accounts SET last_seen=CURRENT_TIMESTAMP WHERE id=?", client.userId());
        } catch (SQLException ignored) { /* A shutdown or temporary outage must not retain presence. */ }
    }

    @OnWebSocketError public void failed(Session session, Throwable error) {
        if (session != null) { clients.remove(session); if (session.isOpen()) session.close(1011, "Connection interrupted"); }
    }

    @OnWebSocketMessage public void message(Session session, String message) {
        Client client = clients.get(session);
        if (message.equals("ping") && client != null) client.acknowledgedAt().set(System.currentTimeMillis());
        else session.close(1008, "Use authenticated HTTP for application writes");
    }

    public static void publish(long userId) {
        Live live = active;
        if (live != null) live.clients.forEach((session, client) -> { if (client.userId() == userId) live.send(session, "refresh"); });
    }

    public static boolean online(long userId) {
        Live live = active;
        return live != null && live.clients.entrySet().stream().anyMatch(entry -> entry.getValue().userId() == userId && entry.getKey().isOpen());
    }

    public static void revokeUser(long userId) {
        Live live = active;
        if (live != null) live.clients.forEach((session, client) -> { if (client.userId() == userId) session.close(1008, "Session expired"); });
    }

    public static void revokeToken(String tokenHash) {
        Live live = active;
        if (live != null) live.clients.forEach((session, client) -> { if (client.tokenHash().equals(tokenHash)) session.close(1000, "Signed out"); });
    }

    public static void shutdown() { Live live = active; if (live != null) live.close(); }

    private void heartbeat() {
        clients.forEach((session, client) -> {
            if (!session.isOpen()) { clients.remove(session); return; }
            if (System.currentTimeMillis() - client.acknowledgedAt().get() > 20000) { session.close(1001, "Connection timed out"); return; }
            try (Connection connection = database.connection()) {
                if (Sql.number(connection, "SELECT COUNT(*) FROM sessions WHERE token_hash=? AND expires_at>CURRENT_TIMESTAMP", client.tokenHash()) == 0) { session.close(1008, "Session expired"); return; }
                Sql.update(connection, "UPDATE accounts SET last_seen=CURRENT_TIMESTAMP WHERE id=?", client.userId());
                send(session, "heartbeat");
            } catch (SQLException error) { session.close(1013, "Live updates unavailable"); }
        });
    }

    private void send(Session session, String message) {
        Client client = clients.get(session);
        if (client == null || !session.isOpen() || !client.pending().compareAndSet(false, true)) return;
        try {
            session.getRemote().sendString(message, new WriteCallback() {
                @Override public void writeSuccess() { client.pending().set(false); }
                @Override public void writeFailed(Throwable error) { client.pending().set(false); clients.remove(session); if (session.isOpen()) session.close(1011, "Connection interrupted"); }
            });
        } catch (RuntimeException error) { client.pending().set(false); clients.remove(session); if (session.isOpen()) session.close(1011, "Connection interrupted"); }
    }

    @Override public void close() {
        heartbeat.shutdownNow(); clients.keySet().forEach(session -> session.close(1001, "Server stopping")); clients.clear();
        if (active == this) active = null;
    }
}
