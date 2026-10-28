package io.github.sjzeero.voltworks.redstone;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class RedstoneTexturesTest {
    static BufferedImage image(String path) throws IOException {
        try(InputStream in = RedstoneTexturesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return ImageIO.read(in);
        }
    }

    static void assertRedstoneRed(String path) throws IOException {
        BufferedImage img = image(path);
        for(int y = 0; y < img.getHeight(); y++)
            for(int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                if((argb >>> 24) == 0)
                    continue;
                int r = argb >> 16 & 0xff, g = argb >> 8 & 0xff, b = argb & 0xff;
                assertTrue(r >= 80 && g * 4 <= r && b * 6 <= r, path + " pixel " + x + "," + y + " is not redstone red: " + r + "," + g + "," + b);
            }
    }

    @Test
    void wireCoilIsRedstoneRed() throws IOException {
        BufferedImage img = image("assets/voltworks/textures/block/redstone_coil.png");
        assertEquals(16, img.getWidth());
        assertEquals(16, img.getHeight());
        assertRedstoneRed("assets/voltworks/textures/block/redstone_coil.png");
    }

    @Test
    void wireItemIsRedstoneRed() throws IOException {
        assertRedstoneRed("assets/voltworks/textures/item/redstone_cable_wire.png");
    }
}
