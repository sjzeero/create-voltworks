package io.github.sjzeero.voltworks.wire;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SpoolRecipesTest {
    static final Map<String, String> SPOOLS = Map.of(
            "voltworks:copper_cord", "voltworks:two_core_wire",
            "voltworks:redstone_cable", "voltworks:redstone_cable_wire");

    static JsonObject json(String path) throws IOException {
        try(InputStream in = SpoolRecipesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    static String id(String full) {
        return full.substring(full.indexOf(':') + 1);
    }

    @Test
    void spoolIsCraftedFromItsWireAndAnEmptySpool() throws IOException {
        for(var e : SPOOLS.entrySet()) {
            JsonObject r = json("data/voltworks/recipe/crafting/" + id(e.getKey()) + ".json");
            assertEquals("minecraft:crafting_shaped", r.get("type").getAsString());
            JsonArray pattern = r.getAsJsonArray("pattern");
            assertEquals(List.of(" w ", "wsw", " w "), pattern.asList().stream().map(j -> j.getAsString()).toList());
            JsonObject key = r.getAsJsonObject("key");
            assertEquals("electroenergetics:empty_spool", key.getAsJsonObject("s").get("item").getAsString());
            assertEquals(e.getValue(), key.getAsJsonObject("w").get("item").getAsString());
            assertEquals(e.getKey(), r.getAsJsonObject("result").get("id").getAsString());
            assertEquals(1, r.getAsJsonObject("result").get("count").getAsInt());
        }
    }

    @Test
    void twoCoreWireIsTwoInsulatedWiresInAKelpSheath() throws IOException {
        JsonObject r = json("data/voltworks/recipe/crafting/two_core_wire.json");
        assertEquals("minecraft:crafting_shapeless", r.get("type").getAsString());
        List<String> in = r.getAsJsonArray("ingredients").asList().stream()
                .map(j -> j.getAsJsonObject().get("item").getAsString()).sorted().toList();
        assertEquals(List.of("electroenergetics:insulated_wire", "electroenergetics:insulated_wire", "minecraft:dried_kelp"), in);
        assertEquals("voltworks:two_core_wire", r.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, r.getAsJsonObject("result").get("count").getAsInt());
        assertFalse(SpoolRecipesTest.class.getClassLoader().getResource("assets/voltworks/models/item/copper_cord_wire.json") != null,
                "old copper_cord_wire item must be gone");
        assertEquals("Two-Core Wire", json("assets/voltworks/lang/en_us.json").get("item.voltworks.two_core_wire").getAsString());
        assertEquals("Двухжильный провод", json("assets/voltworks/lang/ru_ru.json").get("item.voltworks.two_core_wire").getAsString());
    }

    @Test
    void onlyTheSpoolRecipesMakeSpoolsAndEveryWireHasARecipe() throws IOException {
        Path dir = Path.of("src/main/resources/data/voltworks/recipe");
        assertTrue(Files.isDirectory(dir));
        List<Path> files;
        try(Stream<Path> s = Files.walk(dir)) {
            files = s.filter(p -> p.toString().endsWith(".json")).toList();
        }
        for(String wire : SPOOLS.values())
            assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().equals(id(wire) + ".json")), "no recipe for " + wire);
        for(Path p : files) {
            String text = Files.readString(p);
            JsonObject r = JsonParser.parseString(text).getAsJsonObject();
            if(!r.has("result") || !r.get("result").isJsonObject() || !r.getAsJsonObject("result").has("id"))
                continue;
            String out = r.getAsJsonObject("result").get("id").getAsString();
            if(SPOOLS.containsKey(out))
                assertEquals(id(out) + ".json", p.getFileName().toString(), "spool " + out + " also crafted by " + p);
        }
    }

    @Test
    void wiresHaveModelsTexturesAndNames() throws IOException {
        JsonObject en = json("assets/voltworks/lang/en_us.json"), ru = json("assets/voltworks/lang/ru_ru.json");
        for(var e : SPOOLS.entrySet())
            for(String item : List.of(e.getKey(), e.getValue())) {
                String n = id(item);
                json("assets/voltworks/models/item/" + n + ".json");
                try(InputStream png = getClass().getClassLoader().getResourceAsStream("assets/voltworks/textures/item/" + n + ".png")) {
                    assertNotNull(png, "texture " + n);
                }
                assertTrue(en.has("item.voltworks." + n), "en_us " + n);
                assertTrue(ru.has("item.voltworks." + n), "ru_ru " + n);
            }
        assertTrue(en.get("item.voltworks.copper_cord").getAsString().contains("Spool"));
        assertTrue(ru.get("item.voltworks.copper_cord").getAsString().contains("Катушка"));
    }
}
