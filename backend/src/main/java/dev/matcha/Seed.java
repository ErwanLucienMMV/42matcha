package dev.matcha;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;

public final class Seed {
    static final int COUNT = 500;
    private static final String[] FIRST = {"Camille","Alex","Robin","Sasha","Noa","Jules","Lou","Charlie","Morgan","Andrea","Eden","Sam","Ashley","Ari","Taylor","Jamie","Riley","Casey","Jordan","Skyler","Avery","Emery","Rowan","Quinn","Blair"};
    private static final String[] LAST = {"Martin","Bernard","Dubois","Thomas","Robert","Richard","Petit","Durand","Leroy","Moreau","Simon","Laurent","Lefebvre","Michel","Garcia","Roux","Fontaine","Chevalier","Francois","Legrand"};
    static final String[] TAGS = {"coffee","hiking","cinema","music","cooking","books","travel","photography","gaming","running","art","yoga","nature","vegan","cycling","jazz","dogs","design","gardening","dancing"};
    private static final String[] CITIES = {"Paris, FR","Lyon, FR","Marseille, FR","Bordeaux, FR","Lille, FR","Nantes, FR","Strasbourg, FR","Toulouse, FR","Berlin, DE","London, GB","Brussels, BE","Amsterdam, NL"};
    private static final double[][] COORDINATES = {{48.86,2.35},{45.76,4.84},{43.30,5.37},{44.84,-0.58},{50.63,3.06},{47.22,-1.55},{48.58,7.75},{43.60,1.44},{52.52,13.41},{51.51,-0.13},{50.85,4.35},{52.37,4.90}};
    private static final String[] DAYS = {"a slow coffee and a long walk","a market basket and a new recipe","a trail with a view","a bookshop and a quiet afternoon","a camera and an unfamiliar street"};
    private static final String[] STORIES = {"Always curious about a good story.","Happiest when discovering somewhere new.","I make time for little everyday adventures.","A great conversation can change a whole day.","Looking for someone to share the ordinary joys."};
    private Seed() {}

    public static void run(Database database) throws Exception {
        String password = System.getenv("SEED_PASSWORD");
        if (password == null || password.isBlank() || password.startsWith("REPLACE_")) throw new IllegalArgumentException("Set SEED_PASSWORD in ignored .env using scripts/seed.ps1 or scripts/seed.sh.");
        Passwords.validate(password);
        int created = 0;
        for (int i = 0; i < COUNT; i++) {
            final int index = i;
            boolean added = Sql.transaction(database, connection -> {
                String username = username(index);
                var existing = Sql.one(connection, "SELECT id,email FROM accounts WHERE LOWER(username)=?", username);
                if (existing != null) {
                    if (!existing.get("email").equals(username + "@example.test")) throw new IllegalStateException("A non-demo account already uses a reserved fixture username: " + username);
                    return false;
                }
                String first = FIRST[index % FIRST.length], last = LAST[index / FIRST.length % LAST.length];
                long id = Sql.number(connection, "INSERT INTO accounts(username,email,first_name,last_name,password_hash,verified) VALUES (?,?,?,?,?,TRUE) RETURNING id", username, username + "@example.test", first, last, Passwords.hash(password));
                int city = index % CITIES.length;
                double latitude = Math.round((COORDINATES[city][0] + ((index / 12) % 7 - 3) * .01) * 100) / 100.0;
                double longitude = Math.round((COORDINATES[city][1] + ((index / 36) % 7 - 3) * .01) * 100) / 100.0;
                String gender = new String[]{"woman","man","nonbinary"}[index % 3];
                String orientation = new String[]{"bisexual","heterosexual","homosexual","bisexual"}[index % 4];
                LocalDate birth = LocalDate.now(ZoneOffset.UTC).minusYears(22 + index % 39).minusDays(index % 300);
                String biography = "A perfect Sunday: " + DAYS[index % DAYS.length] + ". " + STORIES[(index / 5) % STORIES.length] + " Based around " + CITIES[city] + ", with time for " + TAGS[index % TAGS.length] + ".";
                Sql.update(connection, "INSERT INTO profiles(user_id,gender,orientation,birth_date,biography,city,latitude,longitude) VALUES (?,?,?,?,?,?,?,?)", id, gender, orientation, birth, biography, CITIES[city], latitude, longitude);
                var interests = new LinkedHashSet<String>();
                interests.add(TAGS[index % TAGS.length]); interests.add(TAGS[(index * 3 + 1) % TAGS.length]); interests.add(TAGS[(index * 7 + 2) % TAGS.length]); interests.add(TAGS[(index * 11 + 3) % TAGS.length]);
                for (String tag : interests) {
                    Sql.update(connection, "INSERT INTO tags(name) VALUES (?) ON CONFLICT(name) DO NOTHING", tag);
                    Sql.update(connection, "INSERT INTO profile_tags(user_id,tag_id) SELECT ?,id FROM tags WHERE name=?", id, tag);
                }
                return true;
            });
            if (added) created++;
            if ((i + 1) % 50 == 0) System.out.println("Evaluation profiles checked: " + (i + 1) + "/" + COUNT);
        }
        System.out.println("Evaluation profiles ready: " + COUNT + "; newly created: " + created + ". Existing profiles were preserved.");
    }

    static String username(int index) { return "demo_" + String.format(java.util.Locale.ROOT, "%03d", index + 1); }

    static long[] ids(Database database) throws Exception {
        long[] ids = new long[COUNT];
        try (var connection = database.connection()) {
            for (int i = 0; i < COUNT; i++) ids[i] = Sql.number(connection, "SELECT id FROM accounts WHERE username=? AND email=?", username(i), username(i) + "@example.test");
        }
        return ids;
    }
}
