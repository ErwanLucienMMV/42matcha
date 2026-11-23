package dev.matcha;

import spark.Request;

/** The unpublished API is reachable only through Nginx, which overwrites this header. */
public final class ClientAddress {
    private ClientAddress() {}

    public static String of(Request request) {
        String forwarded = request.headers("X-Forwarded-For");
        return forwarded != null && forwarded.length() <= 45 && forwarded.matches("[0-9a-fA-F:.]+")
                ? forwarded : request.ip();
    }
}
