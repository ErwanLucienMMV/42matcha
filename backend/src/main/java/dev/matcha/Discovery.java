package dev.matcha;

import spark.Request;

public final class Discovery {
    public void register(Api api) {
        api.get("/api/discover", true, context -> Matching.find(context.connection(), context.userId(), filter(context.request()), true));
    }

    public static Matching.Filter filter(Request request) {
        int minimumAge = Input.integer(request.queryParams("min_age"), 18, 18, 120);
        int maximumAge = Input.integer(request.queryParams("max_age"), 120, 18, 120);
        int minimumPopularity = Input.integer(request.queryParams("min_popularity"), 0, 0, 100);
        int maximumPopularity = Input.integer(request.queryParams("max_popularity"), 100, 0, 100);
        if (minimumAge > maximumAge || minimumPopularity > maximumPopularity) throw ApiError.invalid("A range minimum cannot exceed its maximum.");
        String city = request.queryParams("city");
        if (city == null) city = "";
        if (city.length() > 120) throw ApiError.invalid("The location filter is too long.");
        String sort = request.queryParams("sort");
        if (sort == null || sort.isBlank()) sort = "recommended";
        if (!java.util.Set.of("recommended", "age", "location", "popularity", "common_tags").contains(sort)) throw ApiError.invalid("Choose a valid sorting field.");
        String direction = request.queryParams("direction");
        if (direction == null) direction = "";
        if (!java.util.Set.of("", "asc", "desc").contains(direction)) throw ApiError.invalid("Choose an ascending or descending order.");
        return new Matching.Filter(minimumAge, maximumAge, minimumPopularity, maximumPopularity,
                city.trim(), Input.integer(request.queryParams("max_distance"), 20050, 1, 20050),
                Input.integer(request.queryParams("min_common"), 0, 0, 10), sort, direction,
                Input.integer(request.queryParams("page"), 1, 1, 10000), Input.integer(request.queryParams("size"), 20, 1, 50));
    }
}
