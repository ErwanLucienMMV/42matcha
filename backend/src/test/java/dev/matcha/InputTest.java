package dev.matcha;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class InputTest {
    @Test void numericBoundariesAndInjectionAreRejected() {
        assertEquals(24, Input.integer("24", 0, 18, 120));
        assertThrows(ApiError.class, () -> Input.integer("17", 0, 18, 120));
        assertThrows(ApiError.class, () -> Input.integer("18 OR 1=1", 0, 18, 120));
        assertThrows(ApiError.class, () -> Input.id("-1"));
        assertThrows(ApiError.class, () -> Input.id("9223372036854775808"));
    }
}
