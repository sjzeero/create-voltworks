package io.github.sjzeero.voltworks.reel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Every reel has its blockstate, models, loot table, recipe and names in both languages. */
class ReelAssetsTest {
    static final List<String> REELS = List.of("cord_reel", "cable_reel");

    static JsonObject json(String path) throws IOException {
        try (InputStream in = ReelAssetsTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    static boolean exists(String path) {
        return ReelAssetsTest.class.getClassLoader().getResource(path) != null;
    }

    static final int LEVELS = ReelMath.FILL_LEVELS;

    static String model(String reel, int fill) {
        return "assets/voltworks/models/block/" + reel + "_fill" + fill + ".json";
    }

    @Test
    void blockstatesCoverAllFacingsAndFillLevels() throws IOException {
        for (String r : REELS) {
            JsonObject v = json("assets/voltworks/blockstates/" + r + ".json").getAsJsonObject("variants");
            assertEquals(4 * (LEVELS + 1), v.size());
            for (int fill = 0; fill <= LEVELS; fill++)
                for (String f : List.of("north", "east", "south", "west"))
                    assertEquals("voltworks:block/" + r + "_fill" + fill, v.getAsJsonObject("facing=" + f + ",fill=" + fill).get("model").getAsString());
        }
    }

    @Test
    void modelsAndTexturesExist() throws IOException {
        for (String r : REELS) {
            for (int fill = 0; fill <= LEVELS; fill++)
                for (var t : json(model(r, fill)).getAsJsonObject("textures").entrySet()) {
                    String tex = t.getValue().getAsString();
                    if (tex.startsWith("voltworks:"))
                        assertTrue(exists("assets/voltworks/textures/" + tex.substring(tex.indexOf(':') + 1) + ".png"), tex);
                }
            assertEquals("voltworks:block/" + r + "_fill0", json("assets/voltworks/models/item/" + r + ".json").get("parent").getAsString(),
                    "the item shows the empty drum");
        }
        assertTrue(exists("assets/voltworks/textures/block/cable_reel_parts.png"));
        assertTrue(exists("assets/voltworks/textures/item/cord_plug.png"));
        assertTrue(exists("assets/voltworks/textures/item/cable_lug.png"));
        json("assets/voltworks/models/item/reel_plug.json");
    }

    @Test
    void modelStaysInsideTheBlockOnThePixelGrid() throws IOException {
        for (String r : REELS)
            for (int fill = 0; fill <= LEVELS; fill++)
                for (var e : json(model(r, fill)).getAsJsonArray("elements")) {
                    var o = e.getAsJsonObject();
                    for (String k : List.of("from", "to"))
                        for (var c : o.getAsJsonArray(k)) {
                            double d = c.getAsDouble();
                            assertTrue(d >= 0 && d <= 16 && d == Math.rint(d), r + " " + o);
                        }
                }
    }

    /** Coil element of a fill model, or null when the drum is bare. */
    static JsonObject coil(String reel, int fill) throws IOException {
        for (var e : json(model(reel, fill)).getAsJsonArray("elements"))
            if (e.getAsJsonObject().toString().contains("\"#coil\""))
                return e.getAsJsonObject();
        return null;
    }

    @Test
    void emptyDrumIsBareAndTheCoilGrowsOnePixelPerLevel() throws IOException {
        for (String r : REELS) {
            assertNull(coil(r, 0), r + ": no wire on an empty drum");
            for (int fill = 1; fill <= LEVELS; fill++) {
                JsonObject c = coil(r, fill);
                assertNotNull(c, r + " fill " + fill);
                double radius = (c.getAsJsonArray("to").get(1).getAsDouble() - c.getAsJsonArray("from").get(1).getAsDouble()) / 2;
                assertEquals(2 + fill, radius, 1e-9, r + " fill " + fill);
                // texture pixels as big as model pixels
                for (var f : c.getAsJsonObject("faces").entrySet()) {
                    var uv = f.getValue().getAsJsonObject().getAsJsonArray("uv");
                    assertEquals(2 * radius, uv.get(2).getAsDouble() - uv.get(0).getAsDouble(), 1e-9);
                    assertEquals(2 * radius, uv.get(3).getAsDouble() - uv.get(1).getAsDouble(), 1e-9);
                }
            }
        }
    }

    @Test
    void lootTablesDropTheReel() throws IOException {
        for (String r : REELS) {
            JsonObject t = json("data/voltworks/loot_table/blocks/" + r + ".json");
            assertEquals("voltworks:" + r, t.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        }
    }

    @Test
    void recipesUseAnEmptySpoolAndAndesiteCasing() throws IOException {
        for (String r : REELS) {
            JsonObject recipe = json("data/voltworks/recipe/crafting/" + r + ".json");
            assertEquals("voltworks:" + r, recipe.getAsJsonObject("result").get("id").getAsString());
            String keys = recipe.getAsJsonObject("key").toString();
            assertTrue(keys.contains("electroenergetics:empty_spool") && keys.contains("create:andesite_casing"), keys);
        }
    }

    @Test
    void namesInBothLanguages() throws IOException {
        for (String lang : List.of("en_us", "ru_ru")) {
            JsonObject l = json("assets/voltworks/lang/" + lang + ".json");
            for (String r : REELS) {
                assertTrue(l.has("block.voltworks." + r), lang + " " + r);
                assertTrue(l.has("block.voltworks." + r + ".tooltip.summary"), lang + " " + r);
            }
            assertTrue(l.has("item.voltworks.reel_plug"));
            assertTrue(l.has("entity.voltworks.cable_end"), lang);
            for (String k : List.of("loaded", "full", "grabbed", "connected", "retracted", "too_far", "too_short", "snapped", "goggles.wound",
                    "goggles.on_floor", "end_on_floor", "hands_full"))
                assertTrue(l.has("voltworks.cable_reel." + k), lang + " " + k);
        }
        assertEquals("Кабельная лебёдка", json("assets/voltworks/lang/ru_ru.json").get("block.voltworks.cable_reel").getAsString());
    }

    /**
     * The cable end item is 3D: the socket plug for the cord reel (item property voltworks:cord), a copper lug for the
     * cable reel. Both on the 16 px grid with one texel per pixel (no mixels), lying on y = 6 with the cable leaving at
     * the back point CableEndRenderer draws the wire to.
     */
    @Test
    void cableEndModelsAre3dOnThePixelGrid() throws IOException {
        JsonObject item = json("assets/voltworks/models/item/reel_plug.json");
        assertEquals("voltworks:item/cable_lug", item.get("parent").getAsString());
        JsonObject over = item.getAsJsonArray("overrides").get(0).getAsJsonObject();
        assertEquals(1, over.getAsJsonObject("predicate").get("voltworks:cord").getAsInt());
        assertEquals("voltworks:item/cord_plug", over.get("model").getAsString());
        for (String[] m : new String[][]{{"cord_plug", "8", "7.5", "13"}, {"cable_lug", "8", "7", "12"}}) {
            JsonObject model = json("assets/voltworks/models/item/" + m[0] + ".json");
            assertEquals("voltworks:item/" + m[0], model.getAsJsonObject("textures").get("0").getAsString());
            assertTrue(model.has("display"));
            double minY = 99, maxZ = -99;
            boolean backHit = false;
            for (var e : model.getAsJsonArray("elements")) {
                var from = e.getAsJsonObject().getAsJsonArray("from");
                var to = e.getAsJsonObject().getAsJsonArray("to");
                double[] f = new double[3], t = new double[3];
                for (int k = 0; k < 3; k++) {
                    f[k] = from.get(k).getAsDouble();
                    t[k] = to.get(k).getAsDouble();
                    assertEquals(Math.rint(f[k]), f[k], m[0] + ": whole pixels");
                    assertEquals(Math.rint(t[k]), t[k], m[0] + ": whole pixels");
                }
                minY = Math.min(minY, f[1]);
                maxZ = Math.max(maxZ, t[2]);
                double bx = Double.parseDouble(m[1]), by = Double.parseDouble(m[2]), bz = Double.parseDouble(m[3]);
                if (t[2] == bz && f[0] <= bx && bx <= t[0] && f[1] <= by && by <= t[1])
                    backHit = true;
                for (var fe : e.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                    var uv = fe.getValue().getAsJsonObject().getAsJsonArray("uv");
                    double w = Math.abs(uv.get(2).getAsDouble() - uv.get(0).getAsDouble());
                    double h = Math.abs(uv.get(3).getAsDouble() - uv.get(1).getAsDouble());
                    String dir = fe.getKey();
                    double fw = dir.equals("east") || dir.equals("west") ? t[2] - f[2] : t[0] - f[0];
                    double fh = dir.equals("up") || dir.equals("down") ? t[2] - f[2] : t[1] - f[1];
                    assertEquals(fw, w, m[0] + " " + dir + ": one texel per pixel");
                    assertEquals(fh, h, m[0] + " " + dir + ": one texel per pixel");
                }
            }
            assertEquals(6.0, minY, m[0] + " lies on y = 6");
            assertEquals(Double.parseDouble(m[3]), maxZ, m[0] + " back face");
            assertTrue(backHit, m[0] + ": the cable leaves the back face");
        }
    }
}
