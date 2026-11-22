package dev.matcha;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Map;

public final class Preferences {
    public void register(Api api) {
        api.put("/api/me/details", true, context -> {
            var body = Input.body(context.request());
            LocalDate birth;
            try { birth = LocalDate.parse(Input.text(body, "birth_date", 10, 10)); }
            catch (DateTimeParseException error) { throw ApiError.invalid("Choose a valid birth date."); }
            age(birth, LocalDate.now(ZoneOffset.UTC));
            Sql.update(context.connection(), "UPDATE profiles SET birth_date=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", birth, context.userId());
            return Map.of("message", "Your birth date is saved.");
        });
    }

    static int age(LocalDate birth, LocalDate today) {
        int age = Period.between(birth, today).getYears();
        if (birth.isAfter(today) || age < 18 || age > 120) throw ApiError.invalid("Matcha is for adults aged 18 or over. Choose a valid birth date.");
        return age;
    }

    public static long popularity(Connection connection, long userId) throws SQLException {
        return Sql.number(connection, "SELECT LEAST(100,5*(SELECT COUNT(*) FROM likes WHERE target_id=?)+(SELECT COUNT(*) FROM visits WHERE target_id=?))", userId, userId);
    }

    public static void requireEligible(Connection connection, long userId) throws SQLException {
        long complete = Sql.number(connection, "SELECT COUNT(*) FROM profiles p WHERE p.user_id=? AND p.gender IS NOT NULL AND p.birth_date IS NOT NULL AND p.latitude IS NOT NULL AND p.longitude IS NOT NULL AND LENGTH(TRIM(p.city))>0 AND LENGTH(TRIM(p.biography))>0 AND EXISTS(SELECT 1 FROM profile_tags pt WHERE pt.user_id=p.user_id)", userId);
        if (complete == 0) throw new ApiError(403, "profile_incomplete", "Complete your profile, birth date, interests and approximate location before discovering people.");
    }
}
