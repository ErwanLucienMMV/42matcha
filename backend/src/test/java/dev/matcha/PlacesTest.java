package dev.matcha;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PlacesTest {
    @Test void offlineLookupFindsCitiesAndHandlesAccents() {
        Places places = new Places();
        assertTrue(places.search("Paris").stream().anyMatch(place -> place.name().equals("Paris") && place.country().equals("FR")));
        assertFalse(places.search("sao paulo").isEmpty());
        assertEquals("FR", places.nearest(48.86, 2.35).country());
    }

    @Test void coordinatesRejectNonFiniteAndOutOfRangeValues() {
        assertThrows(ApiError.class, () -> Locations.coordinate("NaN", -90, 90));
        assertThrows(ApiError.class, () -> Locations.coordinate("Infinity", -180, 180));
        assertThrows(ApiError.class, () -> Locations.coordinate("91", -90, 90));
        assertEquals(0, Places.distance(48, 2, 48, 2), 0.0001);
        assertTrue(Places.distance(48.86, 2.35, 45.75, 4.85) > 350);
    }
}
