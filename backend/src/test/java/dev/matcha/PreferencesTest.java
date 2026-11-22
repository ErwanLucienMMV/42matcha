package dev.matcha;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PreferencesTest {
    @Test void adulthoodStartsOnTheEighteenthBirthday() {
        LocalDate today = LocalDate.of(2026, 11, 21);
        assertEquals(18, Preferences.age(LocalDate.of(2008, 11, 21), today));
        assertThrows(ApiError.class, () -> Preferences.age(LocalDate.of(2008, 11, 22), today));
        assertThrows(ApiError.class, () -> Preferences.age(today.plusDays(1), today));
        assertThrows(ApiError.class, () -> Preferences.age(today.minusYears(121), today));
    }
}
