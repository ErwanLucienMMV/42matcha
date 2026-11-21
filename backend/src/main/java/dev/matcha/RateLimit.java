package dev.matcha;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class RateLimit {
    private record Window(long start, int count) {}
    private final Map<String, Window> windows = new HashMap<>();

    public synchronized void check(String key, int maximum, int seconds) {
        long now = Instant.now().getEpochSecond();
        if (windows.size() > 10000) windows.entrySet().removeIf(entry -> now - entry.getValue().start() > 3600);
        if (windows.size() > 20000) throw new ApiError(429, "rate_limit", "Please try again later.");
        Window window = windows.get(key);
        if (window == null || now - window.start() >= seconds) window = new Window(now, 0);
        if (window.count() >= maximum) throw new ApiError(429, "rate_limit", "Too many attempts. Please try again later.");
        windows.put(key, new Window(window.start(), window.count() + 1));
    }
}
