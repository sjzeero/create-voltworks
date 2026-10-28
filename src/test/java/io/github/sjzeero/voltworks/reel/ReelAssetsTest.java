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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

public class ReelAssetsTest {
    static final List<String> BIG = List.of("cord_reel", "cable_reel");
    static final List<String> SMALL = List.of("small_cord_reel", "small_cable_reel");
    static final List<String> REELS = List.of("cord_reel", "cable_reel", "small_cord_reel", "small_cable_reel");

    public static JsonObject json(String path) throws IOException {
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

    @Test
    void oneTexelPerModelPixelOnEveryFace() throws IOException {
        for (String r : REELS)
            for (int fill = 0; fill <= LEVELS; fill++)
                for (var e : json(model(r, fill)).getAsJsonArray("elements")) {
                    var o = e.getAsJsonObject();
                    assertFalse(o.has("rotation"), r + " " + o);
                    double[] a = new double[3], b = new double[3];
                    for (int i = 0; i < 3; i++) {
                        a[i] = o.getAsJsonArray("from").get(i).getAsDouble();
                        b[i] = o.getAsJsonArray("to").get(i).getAsDouble();
                    }
                    for (var f : o.getAsJsonObject("faces").entrySet()) {
                        var face = f.getValue().getAsJsonObject();
                        double w, h;
                        switch (f.getKey()) {
                            case "north", "south" -> { w = b[0] - a[0]; h = b[1] - a[1]; }
                            case "east", "west" -> { w = b[2] - a[2]; h = b[1] - a[1]; }
                            default -> { w = b[0] - a[0]; h = b[2] - a[2]; }
                        }
                        int rot = face.has("rotation") ? face.get("rotation").getAsInt() : 0;
                        if (rot % 180 != 0) { double t = w; w = h; h = t; }
                        if (!face.has("uv"))
                            continue;
                        var uv = face.getAsJsonArray("uv");
                        String what = r + " fill " + fill + " " + o.get("name") + " " + f.getKey();
                        assertEquals(w, Math.abs(uv.get(2).getAsDouble() - uv.get(0).getAsDouble()), 1e-9, what + " width");
                        assertEquals(h, Math.abs(uv.get(3).getAsDouble() - uv.get(1).getAsDouble()), 1e-9, what + " height");
                    }
                }
    }

    @Test
    void smallReelCentredOnTheFloor() throws IOException {
        for (String r : SMALL)
            for (int fill = 0; fill <= LEVELS; fill++) {
                double[] lo = {16, 16, 16}, hi = {0, 0, 0};
                for (var e : json(model(r, fill)).getAsJsonArray("elements")) {
                    var o = e.getAsJsonObject();
                    for (int i = 0; i < 3; i++) {
                        lo[i] = Math.min(lo[i], o.getAsJsonArray("from").get(i).getAsDouble());
                        hi[i] = Math.max(hi[i], o.getAsJsonArray("to").get(i).getAsDouble());
                    }
                    if ("cheek".equals(o.get("name").getAsString())) {
                        assertEquals(11, o.getAsJsonArray("to").get(1).getAsDouble(), r + ": frame 10 px over a 1 px base");
                        assertEquals(12, o.getAsJsonArray("to").get(2).getAsDouble() - o.getAsJsonArray("from").get(2).getAsDouble());
                    }
                }
                String what = r + " fill " + fill;
                assertEquals(0, lo[1], what + ": on the floor");
                assertEquals(16, lo[0] + hi[0], 1e-9, what + ": centred across");
                assertTrue(hi[0] - lo[0] <= 14 && hi[2] - lo[2] <= 13 && hi[1] <= 13, what + ": small " + Arrays.toString(lo) + Arrays.toString(hi));
            }
    }

    @Test
    void wireFromTheCoilToTheOutlet() throws IOException {
        for (String r : REELS)
            for (int fill = 0; fill <= LEVELS; fill++) {
                double[] outFrom = null, outTo = null, coilFrom = null, coilTo = null;
                List<double[][]> wire = new ArrayList<>();
                for (var e : json(model(r, fill)).getAsJsonArray("elements")) {
                    var o = e.getAsJsonObject();
                    double[] a = new double[3], b = new double[3];
                    for (int i = 0; i < 3; i++) {
                        a[i] = o.getAsJsonArray("from").get(i).getAsDouble();
                        b[i] = o.getAsJsonArray("to").get(i).getAsDouble();
                    }
                    switch (o.get("name").getAsString()) {
                        case "outlet" -> {
                            if (outFrom == null) { outFrom = a.clone(); outTo = b.clone(); }
                            for (int i = 0; i < 3; i++) { outFrom[i] = Math.min(outFrom[i], a[i]); outTo[i] = Math.max(outTo[i], b[i]); }
                        }
                        case "coil" -> { coilFrom = a; coilTo = b; }
                        case "wire" -> {
                            wire.add(new double[][]{a, b});
                            for (var f : o.getAsJsonObject("faces").entrySet())
                                assertEquals("#coil", f.getValue().getAsJsonObject().get("texture").getAsString());
                        }
                        default -> {}
                    }
                }
                String what = r + " fill " + fill;
                if (fill == 0) {
                    assertTrue(wire.isEmpty(), what + ": no wire on an empty drum");
                    continue;
                }
                assertEquals(1, wire.size(), what + ": one straight piece, no post");
                double[] of = outFrom, ot = outTo, w0 = wire.get(0)[0], w1 = wire.get(0)[1];
                assertEquals(2, w1[0] - w0[0], 1e-9, what + ": 2 px wide");
                assertEquals(2, w1[1] - w0[1], 1e-9, what + ": 2 px high");
                assertEquals((of[0] + ot[0]) / 2, (w0[0] + w1[0]) / 2, 1e-9, what + ": centred across the hole");
                assertEquals((of[1] + ot[1]) / 2, (w0[1] + w1[1]) / 2, 1e-9, what + ": at the hole's height");
                assertEquals(of[2] + 1, w0[2], 1e-9, what + ": starts inside the hole, 1 px in");
                assertEquals(coilFrom[2], w1[2], 1e-9, what + ": ends on the coil's front");
                assertTrue(coilFrom[1] <= w0[1] && w1[1] <= coilTo[1], what + ": leaves the coil's front face");
            }
    }

    static boolean touches(double[] a0, double[] a1, double[] b0, double[] b1) {
        int flush = 0;
        for (int i = 0; i < 3; i++) {
            if (a1[i] < b0[i] || b1[i] < a0[i])
                return false;
            if (a1[i] == b0[i] || b1[i] == a0[i])
                flush++;
        }
        return flush <= 1;
    }

    static JsonObject coil(String reel, int fill) throws IOException {
        for (var e : json(model(reel, fill)).getAsJsonArray("elements"))
            if ("coil".equals(e.getAsJsonObject().get("name").getAsString()))
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
                assertEquals((r.startsWith("small_") ? 1 : 2) + fill, radius, 1e-9, r + " fill " + fill);
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
        for (String r : SMALL) {
            JsonObject toSmall = json("data/voltworks/recipe/crafting/" + r + ".json");
            assertEquals("voltworks:" + r, toSmall.getAsJsonObject("result").get("id").getAsString());
            assertEquals("voltworks:" + r.substring(6), toSmall.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
            JsonObject back = json("data/voltworks/recipe/crafting/" + r.substring(6) + "_from_small.json");
            assertEquals("voltworks:" + r.substring(6), back.getAsJsonObject("result").get("id").getAsString());
            assertEquals("voltworks:" + r, back.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
        }
        for (String r : BIG) {
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

    @Test
    void cableEndsAreSmallInTheHand() throws IOException {
        for (String name : List.of("cord_plug", "cable_lug")) {
            JsonObject display = json("assets/voltworks/models/item/" + name + ".json").getAsJsonObject("display");
            for (String ctx : List.of("firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand", "ground"))
                for (var v : display.getAsJsonObject(ctx).getAsJsonArray("scale"))
                    assertTrue(v.getAsDouble() <= 0.5, name + " " + ctx + " scale " + v);
        }
    }

    @Test
    void cableEndModelsAre3dOnThePixelGrid() throws IOException {
        JsonObject item = json("assets/voltworks/models/item/reel_plug.json");
        assertEquals("voltworks:item/cable_lug", item.get("parent").getAsString());
        JsonObject over = item.getAsJsonArray("overrides").get(0).getAsJsonObject();
        assertEquals(1, over.getAsJsonObject("predicate").get("voltworks:cord").getAsInt());
        assertEquals("voltworks:item/cord_plug", over.get("model").getAsString());
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
                double ra = b, rb = 1 - a;
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
        List<String> models = new ArrayList<>(List.of("item/cord_plug", "item/cable_lug"));
        for (String r : REELS)
            for (int f = 0; f <= LEVELS; f++)
                models.add("block/" + r + "_fill" + f);
        String[] dirs = {"west", "east", "down", "up", "north", "south"};
        for (String m : models) {
            List<double[][]> boxes = new ArrayList<>();
            List<JsonObject> els = new ArrayList<>();
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
                double[][] hole = {{7.5, 6.5, 3.5}, {8.5, 6.5, 3.5}, {7.5, 6.5, 4.5}, {8.5, 6.5, 4.5}};
                for (double[] p : hole)
                    for (double[][] box : boxes)
                        assertFalse(inside(p, box), "cable_lug: hole at " + Arrays.toString(p));
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
    @Test
    void reelFacesAreOpaque() throws IOException {
        for (String r : REELS)
            for (int f = 0; f < LEVELS; f++) {
                JsonObject m = json(model(r, f));
                JsonObject tex = m.getAsJsonObject("textures");
                for (var el : m.getAsJsonArray("elements")) {
                    JsonObject e = el.getAsJsonObject();
                    for (var fe : e.getAsJsonObject("faces").entrySet()) {
                        JsonObject face = fe.getValue().getAsJsonObject();
                        if (!face.has("uv"))
                            continue;
                        String id = tex.get(face.get("texture").getAsString().substring(1)).getAsString();
                        String path = "assets/" + id.substring(0, id.indexOf(':')) + "/textures/" + id.substring(id.indexOf(':') + 1) + ".png";
                        var uv = face.getAsJsonArray("uv");
                        int u0 = uv.get(0).getAsInt(), v0 = uv.get(1).getAsInt(), u1 = uv.get(2).getAsInt(), v1 = uv.get(3).getAsInt();
                        if (id.equals("create:block/axis_top"))
                            assertTrue(Math.min(u0, u1) >= 6 && Math.max(u0, u1) <= 10 && Math.min(v0, v1) >= 6 && Math.max(v0, v1) <= 10,
                                    r + "_fill" + f + " " + e.get("name") + " " + fe.getKey() + ": outside the shaft end of axis_top " + uv);
                        if (e.get("name").getAsString().equals("outlet"))
                            assertNotEquals("#casing", face.get("texture").getAsString(), r + "_fill" + f + ": outlet in clean andesite, not the casing crop");
                        if (!exists(path)) {
                            assertFalse(id.startsWith("voltworks:"), "missing " + path);
                            continue;
                        }
                        BufferedImage img;
                        try (InputStream in = ReelAssetsTest.class.getClassLoader().getResourceAsStream(path)) {
                            img = ImageIO.read(in);
                        }
                        for (int x = Math.min(u0, u1); x < Math.max(u0, u1); x++)
                            for (int y = Math.min(v0, v1); y < Math.max(v0, v1); y++)
                                assertTrue((img.getRGB(x, y) >>> 24) == 255,
                                        r + "_fill" + f + " " + e.get("name") + " " + fe.getKey() + ": see-through texel " + x + "," + y + " of " + id);
                    }
                }
            }
    }
}
