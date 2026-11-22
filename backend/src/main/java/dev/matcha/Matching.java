package dev.matcha;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Matching {
    private Matching() {}
    public record Filter(int minAge, int maxAge, int minPopularity, int maxPopularity,
                         String city, double maxDistance, int minCommon, String sort,
                         String direction, int page, int size) {
        public static Filter defaults() { return new Filter(18, 120, 0, 100, "", 20050, 0, "recommended", "", 1, 20); }
    }
    public record Results(List<Map<String, Object>> items, boolean has_more, int page) {}

    private static final String CANDIDATES = """
        WITH candidates AS (
          SELECT a.id,a.username,a.first_name,a.last_name,p.gender,p.orientation,p.biography,p.city,
            EXTRACT(YEAR FROM AGE(p.birth_date))::INTEGER AS age,a.last_seen,
            (SELECT ph.id FROM photos ph WHERE ph.user_id=a.id AND ph.is_primary LIMIT 1) AS photo_id,
            LEAST(100,5*(SELECT COUNT(*) FROM likes l WHERE l.target_id=a.id)+(SELECT COUNT(*) FROM visits v WHERE v.target_id=a.id))::INTEGER AS popularity,
            (SELECT COUNT(*) FROM profile_tags pt JOIN profile_tags mine ON mine.tag_id=pt.tag_id AND mine.user_id=me.user_id WHERE pt.user_id=p.user_id)::INTEGER AS common_tags,
            12742*ASIN(SQRT(LEAST(1.0,GREATEST(0.0,POWER(SIN(RADIANS(p.latitude-me.latitude)/2),2)+COS(RADIANS(me.latitude))*COS(RADIANS(p.latitude))*POWER(SIN(RADIANS(p.longitude-me.longitude)/2),2))))) AS distance_km
          FROM accounts a JOIN profiles p ON p.user_id=a.id JOIN profiles me ON me.user_id=?
          WHERE a.verified AND a.id<>me.user_id AND p.gender IS NOT NULL
            AND p.birth_date IS NOT NULL AND p.latitude IS NOT NULL AND p.longitude IS NOT NULL
            AND LENGTH(TRIM(p.city))>0 AND LENGTH(TRIM(p.biography))>0
            AND EXISTS(SELECT 1 FROM profile_tags pt WHERE pt.user_id=p.user_id)
            AND NOT EXISTS(SELECT 1 FROM blocks b WHERE (b.actor_id=me.user_id AND b.target_id=a.id) OR (b.target_id=me.user_id AND b.actor_id=a.id))
        """;

    public static Results find(Connection connection, long userId, Filter filter, boolean compatibleOnly) throws SQLException {
        return find(connection, userId, filter, compatibleOnly, List.of());
    }

    public static Results find(Connection connection, long userId, Filter filter, boolean compatibleOnly, List<String> tags) throws SQLException {
        Preferences.requireEligible(connection, userId);
        String compatibility = compatibleOnly ? """
            AND (me.orientation='bisexual' OR (me.orientation='heterosexual' AND me.gender<>p.gender) OR (me.orientation='homosexual' AND me.gender=p.gender))
            AND (p.orientation='bisexual' OR (p.orientation='heterosexual' AND p.gender<>me.gender) OR (p.orientation='homosexual' AND p.gender=me.gender))
            """ : "";
        String sort = switch (filter.sort()) {
            case "recommended" -> "CASE WHEN x.distance_km<=25 THEN 0 ELSE 1 END ASC,(x.common_tags*10+x.popularity/10.0-LEAST(x.distance_km,1000)/20.0) DESC,x.distance_km ASC";
            case "age" -> "x.age " + direction(filter.direction(), "ASC");
            case "location" -> "x.distance_km " + direction(filter.direction(), "ASC");
            case "popularity" -> "x.popularity " + direction(filter.direction(), "DESC");
            case "common_tags" -> "x.common_tags " + direction(filter.direction(), "DESC");
            default -> throw ApiError.invalid("Choose a valid sort order.");
        };
        String tagFilter = tags.isEmpty() ? "" : " AND x.id IN (SELECT pt.user_id FROM profile_tags pt JOIN tags t ON t.id=pt.tag_id WHERE t.name IN ("
                + String.join(",", java.util.Collections.nCopies(tags.size(), "?")) + ") GROUP BY pt.user_id HAVING COUNT(DISTINCT t.name)=?)";
        String sql = CANDIDATES + compatibility + """
            ) SELECT x.* FROM candidates x
              WHERE x.age BETWEEN ? AND ? AND x.popularity BETWEEN ? AND ?
                AND x.city ILIKE ? AND x.distance_km<=? AND x.common_tags>=?
            """ + tagFilter + " ORDER BY " + sort + ",x.id ASC LIMIT ? OFFSET ?";
        var values = new ArrayList<Object>(List.of(userId, filter.minAge(), filter.maxAge(), filter.minPopularity(), filter.maxPopularity(),
                "%" + escapeLike(filter.city()) + "%", filter.maxDistance(), filter.minCommon()));
        values.addAll(tags);
        if (!tags.isEmpty()) values.add(tags.size());
        values.add(filter.size() + 1);
        values.add((filter.page() - 1) * filter.size());
        var rows = Sql.rows(connection, sql, values.toArray());
        boolean more = rows.size() > filter.size();
        var page = new ArrayList<>(rows.subList(0, Math.min(filter.size(), rows.size())));
        for (var row : page) row.put("tags", Sql.rows(connection, "SELECT t.name FROM tags t JOIN profile_tags pt ON pt.tag_id=t.id WHERE pt.user_id=? ORDER BY t.name LIMIT 5", row.get("id")).stream().map(tag -> tag.get("name")).toList());
        return new Results(page, more, filter.page());
    }

    private static String direction(String value, String fallback) {
        if (value == null || value.isEmpty()) return fallback;
        if (value.equals("asc")) return "ASC";
        if (value.equals("desc")) return "DESC";
        throw ApiError.invalid("Choose an ascending or descending order.");
    }

    public static String escapeLike(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }
}
