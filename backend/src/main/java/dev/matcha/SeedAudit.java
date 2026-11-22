package dev.matcha;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;

public final class SeedAudit {
    private SeedAudit() {}
    private static final String DEMO = "a.username ~ '^demo_[0-9]{3}$' AND a.email=a.username||'@example.test'";

    public static void run(Database database) throws Exception {
        long[] ids = Seed.ids(database);
        var checks = new LinkedHashMap<String, Object>();
        try (var connection = database.connection()) {
            long profiles = Sql.number(connection, "SELECT COUNT(*) FROM accounts a JOIN profiles p ON p.user_id=a.id WHERE " + DEMO + " AND a.verified AND p.gender IS NOT NULL AND p.birth_date IS NOT NULL AND p.latitude IS NOT NULL AND p.longitude IS NOT NULL AND LENGTH(p.biography)>0 AND EXISTS(SELECT 1 FROM profile_tags t WHERE t.user_id=a.id)");
            require(profiles >= 500 && ids.length == 500, "At least 500 complete, distinct evaluation profiles are required.");
            long genders = Sql.number(connection, "SELECT COUNT(DISTINCT p.gender) FROM profiles p JOIN accounts a ON a.id=p.user_id WHERE " + DEMO);
            long orientations = Sql.number(connection, "SELECT COUNT(DISTINCT p.orientation) FROM profiles p JOIN accounts a ON a.id=p.user_id WHERE " + DEMO);
            long cities = Sql.number(connection, "SELECT COUNT(DISTINCT p.city) FROM profiles p JOIN accounts a ON a.id=p.user_id WHERE " + DEMO);
            long hashes = Sql.number(connection, "SELECT COUNT(DISTINCT a.password_hash) FROM accounts a WHERE " + DEMO);
            long photoOwners = Sql.number(connection, "SELECT COUNT(DISTINCT ph.user_id) FROM photos ph JOIN accounts a ON a.id=ph.user_id WHERE " + DEMO);
            long photos = Sql.number(connection, "SELECT COUNT(*) FROM photos ph JOIN accounts a ON a.id=ph.user_id WHERE " + DEMO);
            long invalidPhotos = Sql.number(connection, "SELECT COUNT(*) FROM (SELECT a.id,COUNT(ph.id) AS total,COUNT(ph.id) FILTER(WHERE ph.is_primary) AS primary_count FROM accounts a LEFT JOIN photos ph ON ph.user_id=a.id WHERE " + DEMO + " GROUP BY a.id) x WHERE total<1 OR total>5 OR primary_count<>1");
            long kinds = Sql.number(connection, "SELECT COUNT(DISTINCT n.kind) FROM notifications n JOIN accounts a ON a.id=n.recipient_id WHERE " + DEMO);
            long messages = Sql.number(connection, "SELECT COUNT(*) FROM messages m JOIN accounts a ON a.id=m.sender_id WHERE " + DEMO);
            long reports = Sql.number(connection, "SELECT COUNT(*) FROM reports r JOIN accounts a ON a.id=r.reporter_id WHERE " + DEMO);
            long blocks = Sql.number(connection, "SELECT COUNT(*) FROM blocks b JOIN accounts a ON a.id=b.actor_id WHERE " + DEMO);
            long scores = Sql.number(connection, "SELECT COUNT(DISTINCT LEAST(100,5*(SELECT COUNT(*) FROM likes l WHERE l.target_id=a.id)+(SELECT COUNT(*) FROM visits v WHERE v.target_id=a.id))) FROM accounts a WHERE " + DEMO);
            require(genders >= 3 && orientations >= 3 && cities >= 10, "Evaluation profile diversity is insufficient.");
            require(hashes >= 500, "Evaluation accounts must have independent salted password hashes.");
            require(photoOwners >= 500 && invalidPhotos == 0, "Every evaluation profile needs valid photo limits and one primary image.");
            require(kinds == 5 && messages >= 90 && reports > 0 && blocks > 0 && scores >= 3, "Evaluation relationship scenarios are incomplete.");
            checks.put("profiles", profiles); checks.put("genders", genders); checks.put("orientations", orientations); checks.put("cities", cities);
            checks.put("independent_password_hashes", hashes); checks.put("photo_owners", photoOwners); checks.put("photos", photos);
            checks.put("notification_kinds", kinds); checks.put("messages", messages); checks.put("reports", reports); checks.put("blocks", blocks); checks.put("popularity_values", scores);
        }
        System.out.println(new ObjectMapper().writeValueAsString(checks));
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
