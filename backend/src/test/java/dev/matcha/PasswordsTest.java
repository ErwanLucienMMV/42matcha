package dev.matcha;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PasswordsTest {
    @Test void rejectsCommonWordsVariationsAndOversizedUnicode() {
        assertThrows(ApiError.class, () -> Passwords.validate("Password123456!"));
        assertThrows(ApiError.class, () -> Passwords.validate("Football12345!"));
        assertThrows(ApiError.class, () -> Passwords.validate("aA1!" + "é".repeat(40)));
        assertDoesNotThrow(() -> Passwords.validate("Violet!River42"));
    }

    @Test void hashingUsesUniqueSaltsAndVerifiesWithoutPlaintext() {
        String password = "Violet!River42";
        String first = Passwords.hash(password);
        String second = Passwords.hash(password);
        assertNotEquals(first, second);
        assertFalse(first.contains(password));
        assertTrue(Passwords.verify(password, first));
        assertFalse(Passwords.verify("wrong", first));
    }

    @Test void tokensAreUnpredictableAndDigestsAreChecked() {
        String token = Secrets.token();
        assertNotEquals(token, Secrets.token());
        assertTrue(token.length() >= 43);
        assertTrue(Secrets.matches(token, Secrets.digest(token)));
        assertFalse(Secrets.matches("different", Secrets.digest(token)));
    }
}
