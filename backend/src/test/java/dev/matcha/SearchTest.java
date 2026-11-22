package dev.matcha;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchTest {
    @Test void tagsAreNormalizedAndUnsafeInputIsRejected() {
        assertEquals(List.of("coffee", "hiking"), Search.tags("#Coffee, hiking, coffee"));
        assertThrows(ApiError.class, () -> Search.tags("coffee,' OR 1=1"));
        assertThrows(ApiError.class, () -> Search.tags("coffee,,hiking"));
        assertThrows(ApiError.class, () -> Search.tags("a,b,c,d,e,f,g,h,i,j,k"));
    }
}
