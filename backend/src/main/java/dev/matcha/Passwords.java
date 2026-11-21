package dev.matcha;

import at.favre.lib.crypto.bcrypt.BCrypt;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public final class Passwords {
    private static final Set<String> COMMON = Set.of("password", "qwerty", "letmein", "welcome", "admin", "football", "baseball", "basketball", "iloveyou", "monkey", "dragon", "sunshine", "princess", "trustnoone", "whatever", "superman", "master", "secret", "computer", "internet", "hello", "freedom", "summer", "winter", "spring", "autumn", "love", "happiness", "beautiful", "butterfly", "chocolate", "friendship", "university", "international", "extraordinary");
    private Passwords() {}

    public static void validate(String password) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.matches("(?s).*[A-Z].*") || !password.matches("(?s).*[a-z].*")
                || !password.matches("(?s).*\\d.*") || !password.matches("(?s).*[^A-Za-z0-9].*")) {
            throw ApiError.invalid("Use at least 12 characters with upper/lowercase letters, a number, and a symbol (maximum 72 UTF-8 bytes).");
        }
        String letters = password.toLowerCase(Locale.ROOT).replace('0', 'o').replace('3', 'e').replace('4', 'a').replace('5', 's').replace('7', 't').replaceAll("[^a-z]", "");
        String plainLetters = password.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (COMMON.contains(plainLetters) || COMMON.contains(letters) || letters.contains("password") || letters.contains("qwerty") || letters.contains("letmein")) {
            throw ApiError.invalid("Choose a password that is not a common English word or a predictable variation.");
        }
    }

    public static String hash(String password) { return BCrypt.withDefaults().hashToString(12, password.toCharArray()); }
    public static boolean verify(String password, String hash) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72) return false;
        return BCrypt.verifyer().verify(password.toCharArray(), hash).verified;
    }
}
