package io.github.sjzeero.voltworks.battery;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sjzeero.voltworks.reel.ReelAssetsTest;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The battery's blockstate, models (16 px per block, 1:1 uv), connected textures, recipe, loot and names. */
class BatteryAssetsTest {
    static BufferedImage png(String path) throws IOException {
        try (InputStream in = BatteryAssetsTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return ImageIO.read(in);
        }
    }

    static List<String> models() {
        return List.of("battery", "battery_terminals");
    }

    @Test
    void blockstateHasOnlyTheTerminalVariants() throws IOException {
        JsonObject v = ReelAssetsTest.json("assets/voltworks/blockstates/battery.json").getAsJsonObject("variants");
        assertEquals(2, v.size(), "no charge-dependent variants");
        assertEquals("voltworks:block/battery/battery_terminals", v.getAsJsonObject("terminals=true").get("model").getAsString());
        assertEquals("voltworks:block/battery/battery", v.getAsJsonObject("terminals=false").get("model").getAsString());
    }

    static double size(JsonArray from, JsonArray to, int axis) {
        return to.get(axis).getAsDouble() - from.get(axis).getAsDouble();
    }

    @Test
    void modelsAreOnTheBlockGridWithOneToOneUv() throws IOException {
        for (String m : models()) {
            JsonObject model = ReelAssetsTest.json("assets/voltworks/models/block/battery/" + m + ".json");
            for (Map.Entry<String, JsonElement> t : model.getAsJsonObject("textures").entrySet()) {
                String tex = t.getValue().getAsString();
                assertNotNull(png("assets/voltworks/textures/" + tex.substring(tex.indexOf(':') + 1) + ".png"));
            }
            for (JsonElement el : model.getAsJsonArray("elements")) {
                JsonObject e = el.getAsJsonObject();
                assertFalse(e.has("rotation"), m + ": rotated element");
                JsonArray from = e.getAsJsonArray("from"), to = e.getAsJsonArray("to");
                for (int a = 0; a < 3; a++) {
                    double f = from.get(a).getAsDouble(), tt = to.get(a).getAsDouble();
                    assertEquals(Math.rint(f), f, m + ": off-grid");
                    assertEquals(Math.rint(tt), tt, m + ": off-grid");
                    assertTrue(f >= 0 && tt <= 18 && tt > f, m); // posts stand 2 px above the block
                }
                for (Map.Entry<String, JsonElement> fe : e.getAsJsonObject("faces").entrySet()) {
                    JsonObject face = fe.getValue().getAsJsonObject();
                    assertFalse(face.has("rotation"), m + ": rotated uv");
                    if (!face.has("uv"))
                        continue;
                    JsonArray uv = face.getAsJsonArray("uv");
                    double w = Math.abs(uv.get(2).getAsDouble() - uv.get(0).getAsDouble());
                    double h = Math.abs(uv.get(3).getAsDouble() - uv.get(1).getAsDouble());
                    double fw, fh;
                    switch (fe.getKey()) {
                        case "north", "south" -> { fw = size(from, to, 0); fh = size(from, to, 1); }
                        case "east", "west" -> { fw = size(from, to, 2); fh = size(from, to, 1); }
                        default -> { fw = size(from, to, 0); fh = size(from, to, 2); }
                    }
                    assertEquals(fw, w, m + " " + fe.getKey() + ": uv width");
                    assertEquals(fh, h, m + " " + fe.getKey() + ": uv height");
                }
            }
        }
    }

    @Test
    void connectedSheetsMatchTheirSprites() throws IOException {
        for (String t : List.of("battery_top", "battery_side")) {
            BufferedImage base = png("assets/voltworks/textures/block/battery/" + t + ".png");
            BufferedImage sheet = png("assets/voltworks/textures/block/battery/" + t + "_connected.png");
            assertEquals(16, base.getWidth());
            assertEquals(16, base.getHeight());
            assertEquals(64, sheet.getWidth(), t);
            assertEquals(64, sheet.getHeight(), t);
            // Create's rectangle CT: tile (0, 3) is the block with no neighbours
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++)
                    assertEquals(base.getRGB(x, y), sheet.getRGB(x, 48 + y), t + " at " + x + "," + y);
            // every tile is drawn (banks may be any rectangle, not only squares)
            for (int y = 0; y < 64; y++)
                for (int x = 0; x < 64; x++)
                    assertEquals(255, sheet.getRGB(x, y) >>> 24, t + ": hole at " + x + "," + y);
            // the lone tile's corner frame is gone from the fully surrounded tile (2, 1)
            assertNotEquals(base.getRGB(0, 0), sheet.getRGB(32, 16), t);
        }
        assertEquals(16, png("assets/voltworks/textures/block/battery/battery_terminals.png").getWidth());
    }

    @Test
    void postsSitFlushOnTheLidUnderTheWireNodes() throws IOException {
        // BatteryBlock.PLUS / MINUS, in sixteenths: the wire ends at the centre of each post's top
        double[][] nodes = {{5, 18, 8}, {11, 18, 8}};
        JsonObject model = ReelAssetsTest.json("assets/voltworks/models/block/battery/battery_terminals.json");
        List<JsonObject> posts = new ArrayList<>();
        for (JsonElement el : model.getAsJsonArray("elements"))
            if (el.getAsJsonObject().getAsJsonArray("to").get(1).getAsDouble() > 16)
                posts.add(el.getAsJsonObject());
        assertEquals(2, posts.size(), "one solid element per post");
        for (int i = 0; i < 2; i++) {
            JsonArray from = posts.get(i).getAsJsonArray("from"), to = posts.get(i).getAsJsonArray("to");
            assertEquals(16, from.get(1).getAsDouble(), "post stands on the lid");
            assertEquals(nodes[i][1], to.get(1).getAsDouble(), "node at the post top");
            assertEquals(nodes[i][0], (from.get(0).getAsDouble() + to.get(0).getAsDouble()) / 2, "node centred in x");
            assertEquals(nodes[i][2], (from.get(2).getAsDouble() + to.get(2).getAsDouble()) / 2, "node centred in z");
        }
        assertEquals(1, ReelAssetsTest.json("assets/voltworks/models/block/battery/battery.json").getAsJsonArray("elements").size(), "no posts on plain blocks");
    }

    @Test
    void recipeLootAndNames() throws IOException {
        JsonObject r = ReelAssetsTest.json("data/voltworks/recipe/sequenced_assembly/battery.json");
        assertEquals("create:sequenced_assembly", r.get("type").getAsString());
        assertEquals("voltworks:battery", r.getAsJsonArray("results").get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("voltworks:incomplete_battery", r.getAsJsonObject("transitional_item").get("id").getAsString());
        assertEquals(3, r.getAsJsonArray("sequence").size());
        JsonObject loot = ReelAssetsTest.json("data/voltworks/loot_table/blocks/battery.json");
        assertTrue(loot.toString().contains("voltworks:battery"));
        assertNotNull(ReelAssetsTest.json("assets/voltworks/models/item/battery.json"));
        assertNotNull(ReelAssetsTest.json("assets/voltworks/models/item/incomplete_battery.json"));
        JsonObject en = ReelAssetsTest.json("assets/voltworks/lang/en_us.json");
        JsonObject ru = ReelAssetsTest.json("assets/voltworks/lang/ru_ru.json");
        assertEquals("Battery", en.get("block.voltworks.battery").getAsString());
        assertEquals("Батарея", ru.get("block.voltworks.battery").getAsString());
        for (String k : List.of("item.voltworks.incomplete_battery", "block.voltworks.battery.tooltip.summary", "voltworks.node.battery_0",
                "voltworks.node.battery_1", "voltworks.battery.goggles.charge", "voltworks.battery.goggles.bank", "voltworks.battery.goggles.voltage",
                "voltworks.battery.goggles.charging", "voltworks.battery.goggles.discharging", "voltworks.battery.tooltip.charge")) {
            assertTrue(en.has(k), k);
            assertTrue(ru.has(k), k);
        }
    }
}
