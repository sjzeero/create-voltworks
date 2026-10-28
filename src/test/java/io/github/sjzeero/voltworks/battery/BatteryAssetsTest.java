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
    static final int LEVELS = BatterySpec.GAUGE_LEVELS;

    static BufferedImage png(String path) throws IOException {
        try (InputStream in = BatteryAssetsTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return ImageIO.read(in);
        }
    }

    static List<String> models() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i <= LEVELS; i++) {
            out.add("battery_c" + i);
            out.add("battery_top_c" + i);
        }
        return out;
    }

    @Test
    void blockstateCoversEveryChargeAndTerminalState() throws IOException {
        JsonObject v = ReelAssetsTest.json("assets/voltworks/blockstates/battery.json").getAsJsonObject("variants");
        assertEquals(2 * (LEVELS + 1), v.size());
        for (int i = 0; i <= LEVELS; i++) {
            assertEquals("voltworks:block/battery/battery_top_c" + i, v.getAsJsonObject("charge=" + i + ",terminals=true").get("model").getAsString());
            assertEquals("voltworks:block/battery/battery_c" + i, v.getAsJsonObject("charge=" + i + ",terminals=false").get("model").getAsString());
        }
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
                    assertTrue(f >= 0 && tt <= 16 && tt > f, m);
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
        List<String> ct = new ArrayList<>(List.of("battery_top", "battery_bottom"));
        for (int i = 0; i <= LEVELS; i++)
            ct.add("battery_side_" + i);
        for (String t : ct) {
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
            // the outer border colour is on the lone tile's edge but nowhere in the fully surrounded tile (2, 1)
            int border = base.getRGB(0, 0);
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++)
                    assertNotEquals(border, sheet.getRGB(32 + x, 16 + y), t + ": border left inside a bank at " + x + "," + y);
        }
        assertEquals(16, png("assets/voltworks/textures/block/battery/battery_well.png").getWidth());
        assertEquals(16, png("assets/voltworks/textures/block/battery/battery_terminals.png").getWidth());
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
