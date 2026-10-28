package io.github.sjzeero.voltworks.cord;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sjzeero.voltworks.reel.ReelAssetsTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Power strip layout, cord lengths of pulled-out cords, and the strip's assets. */
class PowerStripTest {
    @Test
    void plugBacksSitOnTheStripAndPointOutwards() {
        assertArrayEquals(new double[]{1, 4, 4}, StripGeometry.plugBack(2));
        assertArrayEquals(new double[]{1, 4, 12}, StripGeometry.plugBack(4));
        assertArrayEquals(new double[]{15, 4, 4}, StripGeometry.plugBack(6));
        assertEquals(-1, StripGeometry.cordDir(2)[0]);
        assertEquals(-1, StripGeometry.cordDir(4)[0]);
        assertEquals(1, StripGeometry.cordDir(6)[0]);
        assertArrayEquals(StripGeometry.INPUT_NODE, StripGeometry.node(0));
        assertArrayEquals(StripGeometry.INPUT_NODE, StripGeometry.node(1));
        assertArrayEquals(StripGeometry.plugBack(4), StripGeometry.node(5), "return contact at the same plug back");
        assertFalse(StripGeometry.isOutlet(0));
        assertTrue(StripGeometry.isOutlet(7));
        assertFalse(StripGeometry.isOutlet(8));
    }

    /** The cord plug's body footprint (x 6..10, z 5..11 around the socket slot) moved onto each outlet. */
    private static int[] footprint(int supply) {
        double[] o = StripGeometry.offset(supply);
        int x0 = 6, x1 = 10, z0 = 5, z1 = 11; // symmetric around 8, so the B-side turn keeps it
        return new int[]{x0 + (int) o[0], x1 + (int) o[0], z0 + (int) o[2], z1 + (int) o[2]};
    }

    @Test
    void outletsAreWholePixelsOnTheTopAndNoPlugCoversAnother() {
        for (int s : StripGeometry.OUTLETS) {
            double[] o = StripGeometry.offset(s);
            for (double v : o)
                assertEquals(Math.rint(v), v, "whole-pixel offset only");
            assertEquals(0, o[1], "plugs sit on the 3 px top like on a socket");
            int[] f = footprint(s);
            assertTrue(f[0] >= 1 && f[1] <= 15 && f[2] >= 1 && f[3] <= 15, "plug body over the strip");
            double[] slot = StripGeometry.slot(s);
            assertEquals(3, slot[1]);
        }
        for (int a : StripGeometry.OUTLETS)
            for (int b : StripGeometry.OUTLETS)
                if (a < b) {
                    int[] p = footprint(a), q = footprint(b);
                    boolean overlap = p[0] < q[1] && q[0] < p[1] && p[2] < q[3] && q[2] < p[3];
                    assertFalse(overlap, a + " and " + b);
                }
        // the strip's own cord leaves its +X side away from the +X outlet's plug (z 1..7)
        assertEquals(16, StripGeometry.INPUT_NODE[0]);
        assertTrue(StripGeometry.INPUT_NODE[2] > footprint(6)[3]);
    }

    @Test
    void cordReach() {
        assertTrue(CordReach.fits(4.0, 4.0));
        assertFalse(CordReach.fits(4.2, 4.0));
        assertEquals(4.0, CordReach.laidLength(3.99, 4.0), 1e-9);
        assertTrue(CordReach.laidLength(2.0, 8.0) >= 2.0 && CordReach.laidLength(2.0, 8.0) <= 8.0);
        assertTrue(CordReach.laidLength(0, 8.0) >= 0.05);
        assertEquals(10.0, CordReach.reachOf(10.0, 6.0));
        assertEquals(6.0, CordReach.reachOf(null, 6.0));
        assertEquals(6.0, CordReach.reachOf(3.0, 6.0), "a remembered length shorter than laid is stale");
        assertFalse(CordReach.snaps(100, 4, false), "a carried cord never slips out");
        assertFalse(CordReach.snaps(4 + CordReach.SNAP_SLACK, 4, true));
        assertTrue(CordReach.snaps(4 + CordReach.SNAP_SLACK + 0.1, 4, true));
    }

    @Test
    void assetsAreThereWithOneToOneUvs() throws IOException {
        JsonObject v = ReelAssetsTest.json("assets/voltworks/blockstates/power_strip.json").getAsJsonObject("variants");
        assertEquals(12, v.size());
        for (String face : new String[]{"floor", "wall", "ceiling"})
            for (String f : new String[]{"north", "east", "south", "west"})
                assertTrue(v.has("face=" + face + ",facing=" + f));
        JsonObject model = ReelAssetsTest.json("assets/voltworks/models/block/power_strip.json");
        for (JsonElement t : model.getAsJsonObject("textures").entrySet().stream().map(java.util.Map.Entry::getValue).toList()) {
            String tex = t.getAsString();
            assertNotNull(PowerStripTest.class.getClassLoader().getResource("assets/voltworks/textures/" + tex.substring(tex.indexOf(':') + 1) + ".png"), tex);
        }
        for (JsonElement el : model.getAsJsonArray("elements")) {
            JsonObject e = el.getAsJsonObject();
            assertFalse(e.has("rotation"));
            double[] size = new double[3];
            for (int i = 0; i < 3; i++)
                size[i] = e.getAsJsonArray("to").get(i).getAsDouble() - e.getAsJsonArray("from").get(i).getAsDouble();
            for (var face : e.getAsJsonObject("faces").entrySet()) {
                var uv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                double w = Math.abs(uv.get(2).getAsDouble() - uv.get(0).getAsDouble());
                double h = Math.abs(uv.get(3).getAsDouble() - uv.get(1).getAsDouble());
                double[] want = switch (face.getKey()) {
                    case "up", "down" -> new double[]{size[0], size[2]};
                    case "north", "south" -> new double[]{size[0], size[1]};
                    default -> new double[]{size[2], size[1]};
                };
                assertArrayEquals(want, new double[]{w, h}, "1:1 texels on " + face.getKey());
            }
        }
        assertEquals("voltworks:block/power_strip", ReelAssetsTest.json("assets/voltworks/models/item/power_strip.json").get("parent").getAsString());
        assertEquals("voltworks:power_strip", ReelAssetsTest.json("data/voltworks/loot_table/blocks/power_strip.json")
                .getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("voltworks:power_strip", ReelAssetsTest.json("data/voltworks/recipe/crafting/power_strip.json")
                .getAsJsonObject("result").get("id").getAsString());
    }

    /** Every cord and strip message the code sends is translated, in English and Russian. */
    @Test
    void messagesAreTranslated() throws IOException {
        Set<String> keys = new HashSet<>();
        Pattern p = Pattern.compile("\"(voltworks\\.(?:cord|power_strip|node\\.power_strip)[a-z_.0-9]*)\"");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = p.matcher(Files.readString(f));
                while (m.find())
                    keys.add(m.group(1));
            }
        }
        assertTrue(keys.size() >= 15, keys.toString());
        keys.add("block.voltworks.power_strip");
        for (String lang : new String[]{"en_us", "ru_ru"}) {
            JsonObject l = ReelAssetsTest.json("assets/voltworks/lang/" + lang + ".json");
            for (String k : keys)
                assertTrue(l.has(k), lang + " " + k);
        }
    }
}
