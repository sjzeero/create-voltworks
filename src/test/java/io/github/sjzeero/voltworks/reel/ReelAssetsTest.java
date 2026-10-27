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

    @Test
    void blockstatesCoverAllFacings() throws IOException {
        for (String r : REELS) {
            JsonObject v = json("assets/voltworks/blockstates/" + r + ".json").getAsJsonObject("variants");
            for (String f : List.of("north", "east", "south", "west"))
                assertEquals("voltworks:block/" + r, v.getAsJsonObject("facing=" + f).get("model").getAsString());
        }
    }

    @Test
    void modelsAndTexturesExist() throws IOException {
        for (String r : REELS) {
            JsonObject m = json("assets/voltworks/models/block/" + r + ".json");
            String coil = m.getAsJsonObject("textures").get("coil").getAsString();
            assertTrue(exists("assets/voltworks/textures/block/" + coil.substring(coil.indexOf('/') + 1) + ".png"), coil);
            assertEquals("voltworks:block/" + r, json("assets/voltworks/models/item/" + r + ".json").get("parent").getAsString());
        }
        assertTrue(exists("assets/voltworks/textures/block/cable_reel_parts.png"));
        assertTrue(exists("assets/voltworks/textures/item/reel_plug.png"));
        json("assets/voltworks/models/item/reel_plug.json");
    }

    @Test
    void modelStaysInsideTheBlock() throws IOException {
        for (String r : REELS)
            for (var e : json("assets/voltworks/models/block/" + r + ".json").getAsJsonArray("elements")) {
                var o = e.getAsJsonObject();
                for (String k : List.of("from", "to"))
                    for (var c : o.getAsJsonArray(k))
                        assertTrue(c.getAsDouble() >= 0 && c.getAsDouble() <= 16, r + " " + o);
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
            for (String k : List.of("loaded", "full", "grabbed", "connected", "retracted", "too_far", "too_short", "snapped", "goggles.wound"))
                assertTrue(l.has("voltworks.cable_reel." + k), lang + " " + k);
        }
        assertEquals("Кабельная лебёдка", json("assets/voltworks/lang/ru_ru.json").get("block.voltworks.cable_reel").getAsString());
    }
}
