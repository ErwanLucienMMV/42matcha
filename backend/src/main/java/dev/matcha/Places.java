package dev.matcha;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class Places {
    public record Place(long id, String name, String country, double latitude, double longitude, long population) {
        public String label() { return name + ", " + country; }
    }
    private final List<Place> places;

    public Places() {
        try (var resource = Places.class.getResourceAsStream("/data/cities.tsv")) {
            if (resource == null) throw new IllegalStateException("Missing offline city data");
            try (var reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
                places = reader.lines().map(line -> {
                    String[] values = line.split("\\t", -1);
                    if (values.length != 6) throw new IllegalStateException("Invalid offline city data");
                    return new Place(Long.parseLong(values[0]), values[1], values[2], Double.parseDouble(values[3]), Double.parseDouble(values[4]), Long.parseLong(values[5]));
                }).toList();
            }
        } catch (java.io.IOException error) { throw new IllegalStateException("Could not load offline city data", error); }
    }

    public List<Place> search(String query) {
        String normalized = normalize(query);
        if (normalized.length() < 2 || normalized.length() > 80) throw ApiError.invalid("Search with 2-80 characters.");
        return places.stream().filter(place -> normalize(place.label()).contains(normalized))
                .sorted(Comparator.<Place>comparingInt(place -> normalize(place.name()).equals(normalized) ? 0 : 1)
                        .thenComparing(Comparator.comparingLong(Place::population).reversed())).limit(8).toList();
    }

    public Place nearest(double latitude, double longitude) {
        return places.stream().min(Comparator.comparingDouble(place -> distance(latitude, longitude, place.latitude(), place.longitude()))).orElseThrow();
    }

    public static double distance(double latitude, double longitude, double otherLatitude, double otherLongitude) {
        double dLat = Math.toRadians(otherLatitude - latitude), dLon = Math.toRadians(otherLongitude - longitude);
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(otherLatitude)) * Math.pow(Math.sin(dLon / 2), 2);
        return 6371 * 2 * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
