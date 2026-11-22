package dev.matcha;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.imageio.ImageIO;

public final class SeedFixtures {
    private SeedFixtures() {}

    public static void run(Database database) throws Exception {
        long[] ids = Seed.ids(database);
        for (int i = 0; i < ids.length; i++) {
            final int index = i;
            Sql.transaction(database, connection -> {
                Sql.number(connection, "SELECT user_id FROM profiles WHERE user_id=? FOR UPDATE", ids[index]);
                int count = (int) Sql.number(connection, "SELECT COUNT(*) FROM photos WHERE user_id=?", ids[index]);
                int wanted = index % 25 == 0 ? 3 : 1;
                while (count < wanted) {
                    Sql.update(connection, "INSERT INTO photos(user_id,content,media_type,is_primary) VALUES (?,?,'image/jpeg',?)", ids[index], picture(index, count), count == 0);
                    count++;
                }
                Sql.update(connection, "UPDATE accounts SET last_seen=? WHERE id=? AND last_seen IS NULL", Instant.now().minus(1 + index % 72, ChronoUnit.HOURS), ids[index]);
                return null;
            });
        }
        Sql.transaction(database, connection -> {
            try (var lock = connection.createStatement()) { lock.execute("SELECT pg_advisory_xact_lock(426243)"); }
            if (Sql.number(connection, "SELECT COUNT(*) FROM evaluation_seed_batches WHERE version=1") > 0) {
                System.out.println("Existing evaluation relationship fixtures preserved."); return null;
            }
            for (int i = 0; i < ids.length; i++) {
                for (int j = 1; j <= i % 5; j++) {
                    long target = ids[(i + j * 13) % ids.length];
                    if (Sql.update(connection, "INSERT INTO visits(visitor_id,target_id) VALUES (?,?) ON CONFLICT DO NOTHING", ids[i], target) > 0) Notices.emit(connection, target, ids[i], "visit");
                }
                for (int j = 1; j <= i % 4; j++) like(connection, ids[i], ids[(i + j * 7) % ids.length]);
                if (i % 10 == 0) {
                    long partner = ids[i + 1]; like(connection, ids[i], partner); like(connection, partner, ids[i]);
                    message(connection, ids[i], partner, "Hello! What is your favorite place for a little weekend adventure?", i % 3 == 0);
                    message(connection, partner, ids[i], "Somewhere with good coffee and a long walk. How about you?", i % 4 == 0);
                }
            }
            for (int i = 1; i <= 24; i++) like(connection, ids[i], ids[0]);
            if (Relations.connected(connection, ids[30], ids[31])) {
                Sql.update(connection, "DELETE FROM likes WHERE actor_id=? AND target_id=?", ids[30], ids[31]);
                Notices.emit(connection, ids[31], ids[30], "unlike");
                Sql.update(connection, "INSERT INTO notification_suppressions(recipient_id,actor_id) VALUES (?,?) ON CONFLICT DO NOTHING", ids[30], ids[31]);
            }
            Sql.update(connection, "INSERT INTO blocks(actor_id,target_id) VALUES (?,?) ON CONFLICT DO NOTHING", ids[20], ids[21]);
            Sql.update(connection, "DELETE FROM likes WHERE (actor_id=? AND target_id=?) OR (actor_id=? AND target_id=?)", ids[20], ids[21], ids[21], ids[20]);
            Sql.update(connection, "INSERT INTO reports(reporter_id,target_id,reason) VALUES (?,?,'Synthetic fake-account report fixture for evaluation.') ON CONFLICT DO NOTHING", ids[40], ids[41]);
            Sql.update(connection, "INSERT INTO evaluation_seed_batches(version) VALUES (1)");
            return null;
        });
        System.out.println("Evaluation photos, activity, chat and notification fixtures are ready.");
    }

    private static void like(Connection connection, long actor, long target) throws Exception {
        Relations.lock(connection, actor, target);
        if (Relations.blocked(connection, actor, target)) return;
        int inserted = Sql.update(connection, "INSERT INTO likes(actor_id,target_id) VALUES (?,?) ON CONFLICT DO NOTHING", actor, target);
        if (inserted > 0) {
            if (Relations.connected(connection, actor, target)) {
                Notices.emit(connection, target, actor, "connection"); Notices.emit(connection, actor, target, "connection");
            } else Notices.emit(connection, target, actor, "like");
        }
    }

    private static void message(Connection connection, long sender, long recipient, String body, boolean read) throws Exception {
        if (!Relations.connected(connection, sender, recipient)) return;
        if (Sql.update(connection, "INSERT INTO messages(sender_id,recipient_id,body,is_read) SELECT ?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM messages WHERE sender_id=? AND recipient_id=? AND body=?)", sender, recipient, body, read, sender, recipient, body) > 0) Notices.emit(connection, recipient, sender, "message");
    }

    private static byte[] picture(int index, int variant) throws Exception {
        BufferedImage image = new BufferedImage(360,450,BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float hue = (float) ((index * .61803398875 + variant * .13) % 1);
            Color background = Color.getHSBColor(hue, .25f, .93f), foreground = Color.getHSBColor((hue + .12f) % 1, .40f, .65f);
            graphics.setPaint(new GradientPaint(0,0,background,360,450,foreground)); graphics.fillRect(0,0,360,450);
            graphics.setColor(new Color(255,255,255,90)); graphics.fillOval(150,-60,270,270);
            graphics.setColor(new Color(25,55,40,40)); graphics.fillOval(-100,300,500,260);
            graphics.setColor(new Color(255,255,255,220)); graphics.setFont(new Font("Serif",Font.PLAIN,120));
            String monogram = String.valueOf((char) ('A' + index % 26));
            int width = graphics.getFontMetrics().stringWidth(monogram); graphics.drawString(monogram,(360-width)/2,270);
        } finally { graphics.dispose(); }
        var output = new ByteArrayOutputStream(); ImageIO.write(image,"jpeg",output); return output.toByteArray();
    }
}
