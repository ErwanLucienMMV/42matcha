package dev.matcha;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class Search {
    public void register(Api api) {
        api.get("/api/search", true, context -> Matching.find(context.connection(), context.userId(), Discovery.filter(context.request()), false, tags(context.request().queryParams("tags"))));
    }

    static List<String> tags(String input) {
        if (input == null || input.isBlank()) return List.of();
        if (input.length() > 409) throw ApiError.invalid("Choose at most ten tags.");
        var tags = new LinkedHashSet<String>();
        for (String part : input.split(",", -1)) {
            String tag = part.trim().toLowerCase(Locale.ROOT).replaceFirst("^#", "");
            if (!tag.matches("[a-z0-9][a-z0-9_-]{0,39}")) throw ApiError.invalid("Search with valid interest tags separated by commas.");
            tags.add(tag);
        }
        if (tags.size() > 10) throw ApiError.invalid("Choose at most ten tags.");
        return List.copyOf(tags);
    }
}
