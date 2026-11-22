package dev.matcha;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchTest {
    @Test void tagsAreNormalizedAndUnsafeInputIsRejected() {
        assertEquals(List.of("coffee", "hiking"), Search.tags("#Coffee, hiking, coffee"));
        assertThrows(ApiError.class, () -> Search.tags("coffee,' OR 1=1"));
        assertThrows(ApiError.class, () -> Search.tags("coffee,,hiking"));
    }
}
