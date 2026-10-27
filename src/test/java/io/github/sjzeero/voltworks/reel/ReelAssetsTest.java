package io.github.sjzeero.voltworks.reel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
        // name, back point (px), axis the cable leaves along (0 = x, 2 = z), lowest y
        for (String[] m : new String[][]{{"cord_plug", "12", "4", "8", "0", "1"}, {"cable_lug", "8", "7", "11", "2", "6"}}) {
            JsonObject model = json("assets/voltworks/models/item/" + m[0] + ".json");
            assertEquals("voltworks:item/" + m[0], model.getAsJsonObject("textures").get("0").getAsString());
            assertTrue(model.has("display"));
            double[] back = {Double.parseDouble(m[1]), Double.parseDouble(m[2]), Double.parseDouble(m[3])};
            int axis = Integer.parseInt(m[4]);
            double minY = 99, maxBack = -99;
            boolean backHit = false;
            for (var e : model.getAsJsonArray("elements")) {
                double[] f = new double[3], t = new double[3];
                box(e.getAsJsonObject(), f, t, m[0]);
                minY = Math.min(minY, f[1]);
                maxBack = Math.max(maxBack, t[axis]);
                boolean inside = true;
                for (int k = 0; k < 3; k++)
                    if (k != axis && (back[k] < f[k] || back[k] > t[k]))
                        inside = false;
                if (t[axis] == back[axis] && inside)
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
            assertEquals(Double.parseDouble(m[5]), minY, m[0] + " lowest point");
            assertEquals(back[axis], maxBack, m[0] + " back face");
            assertTrue(backHit, m[0] + ": the cable leaves the middle of the back face");
        }
    }

    private static void box(JsonObject e, double[] f, double[] t, String name) {
        var from = e.getAsJsonArray("from");
        var to = e.getAsJsonArray("to");
        for (int k = 0; k < 3; k++) {
            f[k] = from.get(k).getAsDouble();
            t[k] = to.get(k).getAsDouble();
            assertEquals(Math.rint(f[k]), f[k], name + ": whole pixels");
            assertEquals(Math.rint(t[k]), t[k], name + ": whole pixels");
        }
    }

    /**
     * The plug is modelled standing in a floor socket: its blades are exactly the two dark slots painted on the
     * socket's top face (socket.png through socket_v's up face), and go down into the socket, not through it; the
     * body rests on the top face.
     */
    @Test
    void plugBladesFitTheSocketSlots() throws IOException {
        JsonObject base = json("assets/voltworks/models/block/socket_v.json").getAsJsonArray("elements").get(0).getAsJsonObject();
        double[] bf = new double[3], bt = new double[3];
        box(base, bf, bt, "socket_v");
        JsonObject up = base.getAsJsonObject("faces").getAsJsonObject("up");
        var uv = up.getAsJsonArray("uv");
        double u0 = uv.get(0).getAsDouble(), v0 = uv.get(1).getAsDouble(), u1 = uv.get(2).getAsDouble(), v1 = uv.get(3).getAsDouble();
        assertEquals(90, up.get("rotation").getAsInt(), "slot mapping below assumes the 90 degree up face");
        BufferedImage tex;
        try (InputStream in = ReelAssetsTest.class.getClassLoader().getResourceAsStream("assets/voltworks/textures/block/socket.png")) {
            assertNotNull(in);
            tex = ImageIO.read(in);
        }
        int w = (int) (bt[0] - bf[0]), d = (int) (bt[2] - bf[2]);
        Set<String> slots = new TreeSet<>();
        for (int i = 0; i < w; i++)
            for (int j = 0; j < d; j++) {
                double a = (i + 0.5) / w, b = (j + 0.5) / d;
                double ra = b, rb = 1 - a; // face rotation 90
                int tx = (int) Math.floor(u0 + (u1 - u0) * ra), ty = (int) Math.floor(v0 + (v1 - v0) * rb);
                if ((tex.getRGB(tx, ty) & 0xFFFFFF) == SLOT)
                    slots.add((int) (bf[0] + i) + "," + (int) (bf[2] + j));
            }
        assertEquals(4, slots.size(), "two 2x1 slots on the socket");

        Set<String> blades = new TreeSet<>();
        double bodyBottom = 99;
        for (var e : json("assets/voltworks/models/item/cord_plug.json").getAsJsonArray("elements")) {
            double[] f = new double[3], t = new double[3];
            box(e.getAsJsonObject(), f, t, "cord_plug");
            if (f[1] < bt[1]) {
                assertEquals(bt[1], t[1], "blades end on the socket's top face");
                assertTrue(f[1] >= bf[1], "blades stay inside the socket");
                for (int x = (int) f[0]; x < t[0]; x++)
                    for (int z = (int) f[2]; z < t[2]; z++)
                        assertTrue(blades.add(x + "," + z));
            } else {
                bodyBottom = Math.min(bodyBottom, f[1]);
            }
        }
        assertEquals(slots, blades, "plug blades = socket slots");
        assertEquals(bt[1], bodyBottom, "plug body rests on the socket");
    }


    /**
     * No see-through gaps: the end models have all six faces on every cuboid (so the lug's screw hole, the one
     * intended opening, has copper walls), painted with opaque texels only, and in the reel models every face left
     * out is covered by a neighbouring cuboid.
     */
    @Test
    void modelsAreClosed() throws IOException {
        for (String n : List.of("cord_plug", "cable_lug")) {
            BufferedImage tex;
            try (InputStream in = ReelAssetsTest.class.getClassLoader().getResourceAsStream("assets/voltworks/textures/item/" + n + ".png")) {
                assertNotNull(in);
                tex = ImageIO.read(in);
            }
            for (var e : json("assets/voltworks/models/item/" + n + ".json").getAsJsonArray("elements")) {
                var faces = e.getAsJsonObject().getAsJsonObject("faces");
                assertEquals(6, faces.size(), n + ": all six faces");
                for (var fe : faces.entrySet()) {
                    var uv = fe.getValue().getAsJsonObject().getAsJsonArray("uv");
                    int u0 = uv.get(0).getAsInt(), v0 = uv.get(1).getAsInt(), u1 = uv.get(2).getAsInt(), v1 = uv.get(3).getAsInt();
                    for (int x = Math.min(u0, u1); x < Math.max(u0, u1); x++)
                        for (int y = Math.min(v0, v1); y < Math.max(v0, v1); y++)
                            assertEquals(0xFF, tex.getRGB(x, y) >>> 24, n + " " + fe.getKey() + ": opaque texel " + x + "," + y);
                }
            }
        }
        List<String> models = new java.util.ArrayList<>(List.of("item/cord_plug", "item/cable_lug"));
        for (String r : REELS)
            for (int f = 0; f <= LEVELS; f++)
                models.add("block/" + r + "_fill" + f);
        String[] dirs = {"west", "east", "down", "up", "north", "south"};
        for (String m : models) {
            List<double[][]> boxes = new java.util.ArrayList<>();
            List<JsonObject> els = new java.util.ArrayList<>();
            for (var e : json("assets/voltworks/models/" + m + ".json").getAsJsonArray("elements")) {
                double[] f = new double[3], t = new double[3];
                var o = e.getAsJsonObject();
                for (int k = 0; k < 3; k++) {
                    f[k] = o.getAsJsonArray("from").get(k).getAsDouble();
                    t[k] = o.getAsJsonArray("to").get(k).getAsDouble();
                }
                boxes.add(new double[][]{f, t});
                els.add(o);
            }
            if (m.equals("item/cable_lug")) {
                // the ring lug's screw hole is a real opening through the tongue, walled on all four sides
                double[][] hole = {{7.5, 6.5, 3.5}, {8.5, 6.5, 3.5}, {7.5, 6.5, 4.5}, {8.5, 6.5, 4.5}};
                for (double[] p : hole)
                    for (double[][] box : boxes)
                        assertFalse(inside(p, box), "cable_lug: hole at " + java.util.Arrays.toString(p));
                for (double[] p : new double[][]{{6.5, 6.5, 4}, {9.5, 6.5, 4}, {8, 6.5, 2.5}, {8, 6.5, 5.5}})
                    assertTrue(boxes.stream().anyMatch(box -> inside(p, box)), "cable_lug: ring around the hole");
            }
            for (int i = 0; i < els.size(); i++) {
                double[] f = boxes.get(i)[0], t = boxes.get(i)[1];
                for (int d = 0; d < 6; d++) {
                    if (els.get(i).getAsJsonObject("faces").has(dirs[d]))
                        continue;
                    int ax = d / 2, a = (ax + 1) % 3, b = (ax + 2) % 3;
                    double pos = d % 2 == 0 ? f[ax] - 0.01 : t[ax] + 0.01;
                    for (int s = 0; s < 8; s++)
                        for (int q = 0; q < 8; q++) {
                            double[] p = new double[3];
                            p[ax] = pos;
                            p[a] = f[a] + (t[a] - f[a]) * (s + 0.5) / 8;
                            p[b] = f[b] + (t[b] - f[b]) * (q + 0.5) / 8;
                            boolean covered = false;
                            for (int j = 0; j < boxes.size() && !covered; j++)
                                if (j != i && inside(p, boxes.get(j)))
                                    covered = true;
                            assertTrue(covered, m + ": element " + i + " has no " + dirs[d] + " face and nothing covers it");
                        }
                }
            }
        }
    }

    private static boolean inside(double[] p, double[][] box) {
        for (int k = 0; k < 3; k++)
            if (p[k] < box[0][k] || p[k] > box[1][k])
                return false;
        return true;
    }

    private static final int SLOT = 0x2e383a;
}
