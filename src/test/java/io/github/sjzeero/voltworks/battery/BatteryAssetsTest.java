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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BatteryAssetsTest {
    static final String[] SIDES = {"north", "south", "east", "west", "up", "down"};

    static BufferedImage png(String path) throws IOException {
        try (InputStream in = BatteryAssetsTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return ImageIO.read(in);
        }
    }

    static JsonArray multipart() throws IOException {
        return ReelAssetsTest.json("assets/voltworks/blockstates/battery.json").getAsJsonArray("multipart");
    }

    static String path(String model) {
        return "assets/voltworks/models/" + model.substring(model.indexOf(':') + 1) + ".json";
    }

    static boolean matches(JsonObject when, Map<String, String> state) {
        if (when == null)
            return true;
        if (when.has("OR")) {
            for (JsonElement w : when.getAsJsonArray("OR"))
                if (matches(w.getAsJsonObject(), state))
                    return true;
            return false;
        }
        for (Map.Entry<String, JsonElement> e : when.entrySet())
            if (!e.getValue().getAsString().equals(state.get(e.getKey())))
                return false;
        return true;
    }

    static void conditionKeys(JsonObject when, List<String> out) {
        if (when.has("OR"))
            for (JsonElement w : when.getAsJsonArray("OR"))
                conditionKeys(w.getAsJsonObject(), out);
        else
            out.addAll(when.keySet());
    }

    static List<JsonObject> elements(Map<String, String> state) throws IOException {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement p : multipart()) {
            JsonObject part = p.getAsJsonObject();
            if (matches(part.has("when") ? part.getAsJsonObject("when") : null, state))
                for (JsonElement e : ReelAssetsTest.json(path(part.getAsJsonObject("apply").get("model").getAsString())).getAsJsonArray("elements"))
                    out.add(e.getAsJsonObject());
        }
        return out;
    }

    static Map<String, String> state(int joinedBits, boolean terminals) {
        Map<String, String> s = new HashMap<>();
        for (int i = 0; i < 6; i++)
            s.put(SIDES[i], String.valueOf((joinedBits >> i & 1) == 1));
        s.put("terminals", String.valueOf(terminals));
        return s;
    }

    static double[] vec(JsonObject e, String k) {
        JsonArray a = e.getAsJsonArray(k);
        return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }

    @Test
    void blockstateIsTheBankLookOnly() throws IOException {
        for (JsonElement p : multipart()) {
            JsonObject part = p.getAsJsonObject();
            if (part.has("when")) {
                List<String> keys = new ArrayList<>();
                conditionKeys(part.getAsJsonObject("when"), keys);
                for (String k : keys)
                    assertTrue(List.of(SIDES).contains(k) || k.equals("terminals"), k);
            }
            assertNotNull(ReelAssetsTest.json(path(part.getAsJsonObject("apply").get("model").getAsString())));
        }
        assertEquals("voltworks:block/battery/battery_item", ReelAssetsTest.json("assets/voltworks/models/item/battery.json").get("parent").getAsString());
    }

    static double size(JsonArray from, JsonArray to, int axis) {
        return to.get(axis).getAsDouble() - from.get(axis).getAsDouble();
    }

    @Test
    void modelsAreOnTheBlockGridWithOneToOneUv() throws IOException {
        List<String> models = new ArrayList<>(List.of("voltworks:block/battery/battery_item"));
        for (JsonElement p : multipart())
            models.add(p.getAsJsonObject().getAsJsonObject("apply").get("model").getAsString());
        for (String m : models) {
            JsonObject model = ReelAssetsTest.json(path(m));
            for (Map.Entry<String, JsonElement> t : model.getAsJsonObject("textures").entrySet()) {
                String tex = t.getValue().getAsString();
                if (tex.startsWith("voltworks:"))
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
                    assertTrue(f >= 0 && tt <= (a == 1 ? 18 : 16) && tt > f, m);
                }
                for (Map.Entry<String, JsonElement> fe : e.getAsJsonObject("faces").entrySet()) {
                    JsonObject face = fe.getValue().getAsJsonObject();
                    assertFalse(face.has("rotation"), m + ": rotated uv");
                    assertTrue(model.getAsJsonObject("textures").has(face.get("texture").getAsString().substring(1)), m);
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
    void texturesAreOpaqueWhereUsed() throws IOException {
        for (int bits = 0; bits < 64; bits++)
            for (JsonObject e : elements(state(bits, true)))
                for (Map.Entry<String, JsonElement> fe : e.getAsJsonObject("faces").entrySet()) {
                    JsonObject face = fe.getValue().getAsJsonObject();
                    String key = face.get("texture").getAsString().substring(1);
                    String tex = Map.of("core", "battery/battery_core", "parts", "battery/battery_parts", "bar", "battery/battery_bar",
                            "terminals", "battery/battery_terminals").get(key);
                    if (tex == null)
                        continue;
                    BufferedImage img = png("assets/voltworks/textures/block/" + tex + ".png");
                    JsonArray uv = face.getAsJsonArray("uv");
                    int u0 = uv.get(0).getAsInt(), v0 = uv.get(1).getAsInt(), u1 = uv.get(2).getAsInt(), v1 = uv.get(3).getAsInt();
                    for (int x = Math.min(u0, u1); x < Math.max(u0, u1); x++)
                        for (int y = Math.min(v0, v1); y < Math.max(v0, v1); y++)
                            assertEquals(0xFF, img.getRGB(x, y) >>> 24, tex + " " + x + "," + y);
                }
    }

    @Test
    void noHolesThroughTheRack() throws IOException {
        int[][] dir = {{0, 0, 1}, {0, 0, -1}, {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}};
        for (int bits = 0; bits < 64; bits++) {
            List<JsonObject> els = elements(state(bits, false));
            for (int s = 0; s < 6; s++) {
                if ((bits >> s & 1) == 1)
                    continue;
                int ax = dir[s][0] != 0 ? 0 : dir[s][1] != 0 ? 1 : 2, a = (ax + 1) % 3, b = (ax + 2) % 3;
                for (int i = 0; i < 16; i++)
                    for (int j = 0; j < 16; j++) {
                        boolean hit = false;
                        for (int k = 0; k < 16 && !hit; k++) {
                            double[] p = new double[3];
                            p[a] = i + 0.5;
                            p[b] = j + 0.5;
                            p[ax] = dir[s][ax] > 0 ? k + 0.5 : 15.5 - k;
                            for (JsonObject e : els) {
                                double[] f = vec(e, "from"), t = vec(e, "to");
                                if (f[0] < p[0] && p[0] < t[0] && f[1] < p[1] && p[1] < t[1] && f[2] < p[2] && p[2] < t[2]) {
                                    hit = true;
                                    break;
                                }
                            }
                        }
                        assertTrue(hit, "joined " + Integer.toBinaryString(bits) + ": hole through the " + SIDES[s] + " side at " + i + "," + j);
                    }
            }
        }
    }

    @Test
    void frameOnlyOnTheBanksOuterEdges() throws IOException {
        for (int bits = 0; bits < 64; bits++) {
            List<JsonObject> els = elements(state(bits, false));
            for (JsonObject e : els) {
                if (!"frame".equals(e.get("name").getAsString()))
                    continue;
                double[] f = vec(e, "from"), t = vec(e, "to");
                int outer = 0;
                double[][] lim = {{f[2], 0}, {t[2], 16}, {t[0], 16}, {f[0], 0}, {t[1], 16}, {f[1], 0}};
                for (int s = 0; s < 6; s++)
                    if (lim[s][0] == lim[s][1] && (bits >> s & 1) == 0)
                        outer++;
                assertTrue(outer >= 2, "joined " + Integer.toBinaryString(bits) + ": frame inside the bank " + e);
            }
        }
        long frame = elements(state(0, false)).stream().filter(e -> "frame".equals(e.get("name").getAsString())).count();
        assertEquals(12, frame, "a lone battery: 4 posts and 8 rim beams");
        for (JsonObject e : elements(state(63, false)))
            assertTrue(List.of("cell", "core").contains(e.get("name").getAsString()), e.toString());
    }

    @Test
    void itemIsTheLoneBatteryWithItsPosts() throws IOException {
        JsonArray item = ReelAssetsTest.json("assets/voltworks/models/block/battery/battery_item.json").getAsJsonArray("elements");
        assertEquals(elements(state(0, true)).size(), item.size());
    }

    @Test
    void postsSitFlushOnTheInsulatorBarUnderTheWireNodes() throws IOException {
        double[][] nodes = {{5, 18, 8}, {11, 18, 8}};
        List<JsonObject> posts = new ArrayList<>();
        JsonObject bar = null;
        for (JsonObject e : elements(state(0, true))) {
            if (vec(e, "to")[1] > 16)
                posts.add(e);
            if ("terminal_bar".equals(e.get("name").getAsString()))
                bar = e;
        }
        assertEquals(2, posts.size(), "one solid element per post");
        assertNotNull(bar);
        for (int i = 0; i < 2; i++) {
            double[] f = vec(posts.get(i), "from"), t = vec(posts.get(i), "to");
            assertEquals(16, f[1], "post stands on the top");
            assertEquals(vec(bar, "to")[1], f[1], "post stands on the insulator bar");
            assertTrue(vec(bar, "from")[0] <= f[0] && t[0] <= vec(bar, "to")[0] && vec(bar, "from")[2] <= f[2] && t[2] <= vec(bar, "to")[2]);
            assertEquals(nodes[i][1], t[1], "node at the post top");
            assertEquals(nodes[i][0], (f[0] + t[0]) / 2, "node centred in x");
            assertEquals(nodes[i][2], (f[2] + t[2]) / 2, "node centred in z");
        }
        for (JsonObject e : elements(state(0, false)))
            assertTrue(vec(e, "to")[1] <= 16, "no posts on plain blocks");
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
