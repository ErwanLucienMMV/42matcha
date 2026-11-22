package dev.matcha;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

public final class Photos {
    static { ImageIO.setUseCache(false); }
    private final RateLimit limits = new RateLimit();

    public void register(Api api) {
        api.post("/api/me/photos", true, context -> {
            limits.check("photos:" + context.userId(), 60, 3600);
            String type = context.request().contentType();
            if (type == null || !(type.equals("image/jpeg") || type.equals("image/png"))) throw new ApiError(415, "unsupported_image", "Upload a JPEG or PNG image.");
            byte[] safe = sanitize(Input.bytes(context.request(), 5 * 1024 * 1024), type);
            Sql.number(context.connection(), "SELECT user_id FROM profiles WHERE user_id=? FOR UPDATE", context.userId());
            long count = Sql.number(context.connection(), "SELECT COUNT(*) FROM photos WHERE user_id=?", context.userId());
            if (count >= 5) throw ApiError.invalid("You can have up to five photos. Remove one before adding another.");
            long id = Sql.number(context.connection(), "INSERT INTO photos(user_id,content,media_type,is_primary) VALUES (?,?,?,?) RETURNING id", context.userId(), safe, type, count == 0);
            context.response().status(201);
            return Map.of("id", id);
        });
        api.post("/api/me/photos/:id/primary", true, context -> {
            long id = Input.id(context.request().params("id"));
            Sql.number(context.connection(), "SELECT user_id FROM profiles WHERE user_id=? FOR UPDATE", context.userId());
            if (Sql.number(context.connection(), "SELECT COUNT(*) FROM photos WHERE id=? AND user_id=?", id, context.userId()) == 0) throw ApiError.missing();
            Sql.update(context.connection(), "UPDATE photos SET is_primary=FALSE WHERE user_id=? AND is_primary", context.userId());
            Sql.update(context.connection(), "UPDATE photos SET is_primary=TRUE WHERE id=? AND user_id=?", id, context.userId());
            return Map.of("message", "Profile photo updated.");
        });
        api.delete("/api/me/photos/:id", true, context -> {
            long id = Input.id(context.request().params("id"));
            Sql.number(context.connection(), "SELECT user_id FROM profiles WHERE user_id=? FOR UPDATE", context.userId());
            var removed = Sql.one(context.connection(), "DELETE FROM photos WHERE id=? AND user_id=? RETURNING is_primary", id, context.userId());
            if (removed == null) throw ApiError.missing();
            if (Boolean.TRUE.equals(removed.get("is_primary"))) Sql.update(context.connection(), "UPDATE photos SET is_primary=TRUE WHERE id=(SELECT id FROM photos WHERE user_id=? ORDER BY id LIMIT 1)", context.userId());
            return Map.of("message", "Photo removed.");
        });
        api.image("/api/photos/:id", context -> {
            long id = Input.id(context.request().params("id"));
            try (var statement = Sql.statement(context.connection(), "SELECT ph.content,ph.media_type FROM photos ph JOIN accounts a ON a.id=ph.user_id WHERE ph.id=? AND a.verified AND NOT EXISTS(SELECT 1 FROM blocks b WHERE (b.actor_id=? AND b.target_id=ph.user_id) OR (b.target_id=? AND b.actor_id=ph.user_id))", id, context.userId(), context.userId()); var row = statement.executeQuery()) {
                if (!row.next()) throw ApiError.missing();
                return new Api.Image(row.getBytes("content"), row.getString("media_type"));
            }
        });
    }

    static byte[] sanitize(byte[] bytes, String mediaType) {
        if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024) throw new ApiError(413, "image_too_large", "Photos must be no larger than 5 MiB.");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw ApiError.invalid("This file is not a supported image.");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(format.equals("png") && mediaType.equals("image/png")) && !((format.equals("jpeg") || format.equals("jpg")) && mediaType.equals("image/jpeg"))) throw ApiError.invalid("The photo content does not match its file type.");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 16000000) throw ApiError.invalid("Photo dimensions must be at most 4096 pixels per side and 16 megapixels.");
                BufferedImage image = reader.read(0);
                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(image, format.equals("png") ? "png" : "jpeg", output)) throw ApiError.invalid("This photo could not be processed.");
                byte[] safe = output.toByteArray();
                if (safe.length > 5 * 1024 * 1024) throw new ApiError(413, "image_too_large", "The decoded photo is too large.");
                return safe;
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException error) {
            throw ApiError.invalid("This image is damaged or unsupported.");
        }
    }
}
