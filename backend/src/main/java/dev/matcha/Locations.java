package dev.matcha;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public final class Locations {
    private final Places places = new Places();

    public void register(Api api) {
        api.get("/api/locations", true, context -> {
            String query = context.request().queryParams("q");
            if (query == null) throw ApiError.invalid("Enter a city to search.");
            return places.search(query);
        });
        api.get("/api/locations/nearest", true, context -> places.nearest(
                coordinate(context.request().queryParams("latitude"), -90, 90),
                coordinate(context.request().queryParams("longitude"), -180, 180)));
        api.put("/api/me/location", true, context -> {
            var body = Input.body(context.request());
            String city = Input.text(body, "city", 1, 120);
            double latitude = coordinate(body.get("latitude"), -90, 90);
            double longitude = coordinate(body.get("longitude"), -180, 180);
            var consent = body.get("gps_consent");
            if (consent == null || !consent.isBoolean()) throw ApiError.invalid("Specify your GPS consent choice.");
            String source = Input.choice(body, "source", java.util.Set.of("manual", "gps"));
            if (source.equals("gps") && !consent.asBoolean()) throw ApiError.forbidden("GPS location requires your explicit consent.");
            Sql.update(context.connection(), "UPDATE profiles SET city=?,latitude=?,longitude=?,gps_consent=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",
                    city, Math.round(latitude * 100) / 100.0, Math.round(longitude * 100) / 100.0, source.equals("gps") && consent.asBoolean(), context.userId());
            return Map.of("message", "Your approximate location is saved.");
        });
    }

    static double coordinate(JsonNode value, double min, double max) {
        if (value == null || !value.isNumber()) throw ApiError.invalid("Provide valid approximate coordinates.");
        return validate(value.asDouble(), min, max);
    }

    static double coordinate(String value, double min, double max) {
        try { return validate(Double.parseDouble(value), min, max); }
        catch (NumberFormatException | NullPointerException error) { throw ApiError.invalid("Provide valid approximate coordinates."); }
    }

    private static double validate(double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) throw ApiError.invalid("Coordinates are outside the valid range.");
        return value;
    }
}
