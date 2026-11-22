package dev.matcha;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PhotosTest {
    @Test void activeContentAndTypeSpoofingAreRejected() throws Exception {
        assertThrows(ApiError.class, () -> Photos.sanitize("<svg onload='alert(1)'/>".getBytes(java.nio.charset.StandardCharsets.UTF_8), "image/png"));
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", output);
        assertThrows(ApiError.class, () -> Photos.sanitize(output.toByteArray(), "image/jpeg"));
        assertTrue(Photos.sanitize(output.toByteArray(), "image/png").length > 0);
    }

    @Test void excessiveDecodedDimensionsAreRejected() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4097, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        assertThrows(ApiError.class, () -> Photos.sanitize(output.toByteArray(), "image/png"));
    }
}
