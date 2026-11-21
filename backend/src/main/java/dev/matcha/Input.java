package dev.matcha;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Set;
import spark.Request;

public final class Input {
    private static final ObjectMapper JSON = new ObjectMapper();
    private Input() {}

    public static JsonNode body(Request request) {
        if (request.contentType() == null || !request.contentType().toLowerCase(Locale.ROOT).startsWith("application/json")) {
            throw new ApiError(415, "unsupported_media_type", "Send an application/json request.");
        }
        byte[] bytes = request.bodyAsBytes();
        if (bytes.length > 65536) throw new ApiError(413, "request_too_large", "This form is too large.");
        try {
            JsonNode value = JSON.readTree(bytes);
            if (value == null || !value.isObject()) throw ApiError.invalid("Provide a JSON object.");
            return value;
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw ApiError.invalid("Provide valid JSON.");
        } catch (java.io.IOException error) {
            throw ApiError.invalid("The request could not be read.");
        }
    }

    public static String text(JsonNode body, String key, int minimum, int maximum) {
        JsonNode value = body.get(key);
        if (value == null || !value.isTextual()) throw ApiError.invalid(key + " is required.");
        String text = value.asText().trim();
        if (text.length() < minimum || text.length() > maximum || text.indexOf('\0') >= 0) {
            throw ApiError.invalid(key + " must contain " + minimum + " to " + maximum + " characters.");
        }
        return text;
    }

    public static String choice(JsonNode body, String key, Set<String> choices) {
        String value = text(body, key, 1, 40);
        if (!choices.contains(value)) throw ApiError.invalid("Choose a valid " + key + ".");
        return value;
    }

    public static long id(String value) {
        try {
            long id = Long.parseLong(value);
            if (id < 1) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException error) { throw ApiError.invalid("Invalid identifier."); }
    }

    public static int integer(String value, int fallback, int min, int max) {
        if (value == null || value.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException error) { throw ApiError.invalid("A numeric filter is out of range."); }
    }

    public static String email(JsonNode body, String key) {
        String email = text(body, key, 3, 254).toLowerCase(Locale.ROOT);
        if (!email.matches("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,63}$")) {
            throw ApiError.invalid("Provide a valid email address.");
        }
        return email;
    }
}
