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
        assertTrue(exists("assets/voltworks/textures/item/reel_plug.png"));
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
}
