package dev.matcha;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RateLimitTest {
    @Test void blocksExhaustedKeysWithoutBlockingAnotherClient() {
        var limits = new RateLimit();
        limits.check("register:client-one", 2, 3600);
        limits.check("register:client-one", 2, 3600);
        assertEquals(429, assertThrows(ApiError.class, () -> limits.check("register:client-one", 2, 3600)).status);
        assertDoesNotThrow(() -> limits.check("register:client-two", 2, 3600));
    }
}
