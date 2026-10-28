package io.github.sjzeero.voltworks.cord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sjzeero.voltworks.reel.ReelAssetsTest;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PowerStripTest {
    private static final double EPS = 1e-5;

    private static void near(Vector3d want, Vector3d got, String what) {
        assertTrue(want.distance(got) < EPS, what + ": " + want + " vs " + got);
    }

    @Test
    void nodeIdsFollowTheSocketOnes() {
        assertEquals(12, StripGeometry.NODES);
        assertEquals(SocketBlock.CORD_NEGATIVE + 1, StripGeometry.INPUT);
        for (int id = 0; id < StripGeometry.NODES; id++) {
            assertEquals(id >= 4, StripGeometry.isStripNode(id), "strip node " + id);
            assertEquals(id == 4 || id == 5, StripGeometry.isInput(id), "input " + id);
            assertEquals(id >= 6, StripGeometry.isOutlet(id), "outlet " + id);
        }
        assertArrayEquals(new int[]{0, 1, 2}, new int[]{StripGeometry.index(6), StripGeometry.index(9), StripGeometry.index(10)});
        assertEquals(-1, StripGeometry.index(4));
    }

    @Test
    void outletsSitInTheirCellsOnTopOfTheBody() {
        for (int s : StripGeometry.OUTLETS) {
            int i = StripGeometry.index(s);
            int cx = StripGeometry.centerX(s);
            assertEquals((StripGeometry.CELL_X[i] + StripGeometry.CELL_X[i + 1]) / 2, cx);
            Vector3d back = StripGeometry.plugBack(s);
            assertEquals(StripGeometry.BODY_TOP + 1, back.y, "plug back one pixel over the top");
            assertEquals(s, StripGeometry.outletAt(new Vector3d(cx, StripGeometry.BODY_TOP, 8)), "click on the cell picks it");
            assertEquals(s, StripGeometry.outletAt(back), "click on the plug picks it");
            near(back, StripGeometry.node(s), "positive node");
            near(back, StripGeometry.node(s + 1), "return node at the same plug");
            near(new Vector3d(StripGeometry.CORD_DIR), StripGeometry.dir(s), "outlet cords leave sideways");
        }
        assertEquals(-1, StripGeometry.outletAt(new Vector3d(-2, 3, 8)), "the grommet is no outlet");
        assertEquals(-1, StripGeometry.outletAt(new Vector3d(8, 3, 15)));
        near(new Vector3d(StripGeometry.INPUT_NODE), StripGeometry.node(4), "input");
        near(new Vector3d(StripGeometry.INPUT_NODE), StripGeometry.node(5), "input return");
        near(new Vector3d(-1, 0, 0), StripGeometry.dir(4), "the cord leaves the short end");
    }

    @Test
    void plugFootprintsStayOnTheBodyAndApart() {
        int[][] f = new int[3][];
        for (int i = 0; i < 3; i++) {
            f[i] = StripGeometry.plugFootprint(StripGeometry.OUTLETS[i]);
            assertTrue(f[i][0] >= StripGeometry.CELL_X[i] && f[i][1] <= StripGeometry.CELL_X[i + 1], "inside its cell " + i);
            assertTrue(f[i][2] >= StripGeometry.BODY_Z0 && f[i][3] <= StripGeometry.BODY_Z1, "inside the body " + i);
        }
        for (int a = 0; a < 3; a++)
            for (int b = a + 1; b < 3; b++)
                assertFalse(f[a][0] < f[b][1] && f[b][0] < f[a][1], a + " overlaps " + b);
    }

    @Test
    void plugPoseLandsThePlugBackOnTheNode() {
        Vector3d origin = new Vector3d(10, 64, -3);
        for (double yaw : new double[]{0, 0.7, Math.PI / 2, 2.5, -1.9}) {
            Vector3d at = new Vector3d(11.3, 64.0, -1.6);
            for (int s : StripGeometry.OUTLETS) {
                Matrix4f m = StripGeometry.plugPose(at, yaw, origin, s);
                Vector3f back = m.transformPosition(new Vector3f(12 / 16f, 4 / 16f, 8 / 16f));
                Vector3d got = new Vector3d(back.x, back.y, back.z).add(origin);
                near(StripGeometry.world(at, yaw, StripGeometry.node(s)), got, "plug back " + s + " yaw " + yaw);
                Vector3f out = m.transformDirection(new Vector3f(1, 0, 0));
                near(StripGeometry.turn(yaw, StripGeometry.CORD_DIR), new Vector3d(out.x, out.y, out.z), "plug cord direction");
            }
        }
    }

    @Test
    void stripTurnsItsInputTowardTheAnchor() {
        Vector3d at = new Vector3d(0.5, 70, 0.5);
        for (double[] d : new double[][]{{2, 0}, {-3, 1}, {0.4, -2.5}, {-1, -1}}) {
            double yaw = StripGeometry.yawToward(d[0], d[1], 0);
            Vector3d dir = StripGeometry.turn(yaw, StripGeometry.INPUT_DIR);
            Vector3d want = new Vector3d(d[0], 0, d[1]).normalize();
            near(want, dir, "input faces the anchor");
            Vector3d input = StripGeometry.world(at, yaw, StripGeometry.INPUT_NODE);
            Vector3d anchor = new Vector3d(at).add(want.mul(StripGeometry.CENTER_REACH + StripGeometry.INPUT_ARM));
            assertTrue(input.distance(anchor.x, at.y + 1 / 16.0, anchor.z) <= StripGeometry.CORD_LENGTH + EPS, "within cord reach");
        }
        assertEquals(1.25, StripGeometry.yawToward(0.01, 0.01, 1.25), "too close keeps the old yaw");
    }

    @Test
    void worldAndUnturnAreInverse() {
        Vector3d at = new Vector3d(-4.2, 12, 7.9);
        double yaw = 1.1;
        for (Vector3d px : new Vector3d[]{new Vector3d(StripGeometry.INPUT_NODE), StripGeometry.plugBack(8), new Vector3d(0, 0, 0)}) {
            Vector3d w = StripGeometry.world(at, yaw, px);
            near(px, StripGeometry.unturn(yaw, new Vector3d(w).sub(at)), "round trip");
        }
    }

    @Test
    void placementMatchesTheGeometry() {
        StripLinks.Placement p = new StripLinks.Placement(new Vec3(3.5, 65, 9.25), 0.8);
        for (int id = 4; id < 12; id++) {
            Vec3 n = p.node(id);
            near(StripGeometry.world(new Vector3d(3.5, 65, 9.25), 0.8, StripGeometry.node(id)), new Vector3d(n.x, n.y, n.z), "node " + id);
            Vec3 d = p.dir(id);
            assertEquals(1, d.length(), EPS);
        }
        assertEquals(p.node(6).y, p.node(10).y, EPS, "outlets on one level");
        assertTrue(p.node(6).distanceTo(p.node(8)) > 5 / 16.0);
    }

    private static boolean eeBreaks(double distance, double length) {
        return distance - length > 1.0 && distance / length > 1.4;
    }

    @Test
    void stripCordIsEightBlocksAndItsWireNeverSnapsWithinReach() {
        assertEquals(8.0, StripGeometry.CORD_LENGTH);
        assertTrue(eeBreaks(2.7, CordReach.laidLength(1.5, 4.0)), "a wire laid 1.5 blocks long used to snap at about 2.6 blocks");
        for (double d = 0; d <= StripGeometry.CORD_LENGTH + 3; d += 0.05) {
            double len = StripGeometry.cordLength(d, StripGeometry.CORD_LENGTH);
            assertTrue(len >= d - 1e-9, "never shorter than the gap at " + d);
            assertFalse(eeBreaks(d, len), "EE keeps the wire at " + d);
            if (d <= StripGeometry.CORD_LENGTH - 0.5)
                assertEquals(d + StripGeometry.slack(d), len, 1e-9, "a little slack while in reach");
            if (d >= StripGeometry.CORD_LENGTH)
                assertEquals(d, len, 1e-9, "taut past the reach");
        }
        assertTrue(StripGeometry.cordLength(3.9, 4.0) <= 4.0);
    }

    @Test
    void carriedStripIsHeldInFrontAndPulledOnTheCord() {
        Vector3d eye = new Vector3d(0.5, 65.62, 0.5);
        Vector3d look = new Vector3d(1, 0, 0);
        Vector3d hold = StripGeometry.holdTarget(eye, look);
        near(new Vector3d(0.5 + StripGeometry.CARRY_DISTANCE, 65.62 - StripGeometry.CARRY_DROP, 0.5), hold, "in front of the eyes");
        Vector3d anchor = new Vector3d(-3, 64.25, 0.5);
        near(hold, StripGeometry.clampToCord(hold, anchor), "inside the reach it is not held back");
        assertTrue(StripGeometry.CENTER_REACH > 7.0, "no hidden 2 or 4 block cap");
        for (double walk = 0; walk <= 12; walk += 0.25) {
            Vector3d e = new Vector3d(eye).add(walk, 0, 0);
            Vector3d at = StripGeometry.clampToCord(StripGeometry.holdTarget(e, look), anchor);
            assertTrue(at.distance(anchor) <= StripGeometry.CENTER_REACH + EPS);
            double yaw = StripGeometry.yawToward(anchor.x - at.x, anchor.z - at.z, 0);
            Vector3d input = StripGeometry.world(at, yaw, StripGeometry.INPUT_NODE);
            assertTrue(input.distance(anchor) <= StripGeometry.CORD_LENGTH + EPS, "the input stays on the cord at walk " + walk);
            boolean slip = StripGeometry.slips(e, at);
            assertEquals(e.distance(at) > 7.5, slip);
        }
        Vector3d far = new Vector3d(anchor).add(StripGeometry.CENTER_REACH, 0, 0);
        assertFalse(StripGeometry.slips(new Vector3d(far).add(7.4, 0, 0), far));
        assertTrue(StripGeometry.slips(new Vector3d(far).add(7.6, 0, 0), far));
        Vector3d side = StripGeometry.clampToCord(new Vector3d(anchor).add(6, 0, 6), anchor);
        assertEquals(StripGeometry.CENTER_REACH, side.distance(anchor), EPS, "pulled along the cord's sphere toward the player");
        assertEquals(side.x - anchor.x, side.z - anchor.z, EPS);
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
    void modelIsWholePixelsWithOneToOneUvs() throws IOException {
        JsonObject v = ReelAssetsTest.json("assets/voltworks/blockstates/power_strip.json").getAsJsonObject("variants");
        assertEquals(12, v.size(), "legacy blocks still load before they are converted");
        JsonObject model = ReelAssetsTest.json("assets/voltworks/models/block/power_strip.json");
        assertEquals("voltworks:block/power_strip", model.getAsJsonObject("textures").get("strip").getAsString());
        assertNotNull(PowerStripTest.class.getClassLoader().getResource("assets/voltworks/textures/block/power_strip.png"));
        int[] body = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, 0};
        for (JsonElement el : model.getAsJsonArray("elements")) {
            JsonObject e = el.getAsJsonObject();
            assertFalse(e.has("rotation"));
            JsonArray from = e.getAsJsonArray("from"), to = e.getAsJsonArray("to");
            double[] size = new double[3];
            for (int i = 0; i < 3; i++) {
                assertEquals(Math.rint(from.get(i).getAsDouble()), from.get(i).getAsDouble(), "whole pixels");
                assertEquals(Math.rint(to.get(i).getAsDouble()), to.get(i).getAsDouble(), "whole pixels");
                size[i] = to.get(i).getAsDouble() - from.get(i).getAsDouble();
            }
            if (size[1] == StripGeometry.BODY_TOP) {
                body[0] = Math.min(body[0], from.get(0).getAsInt());
                body[1] = Math.max(body[1], to.get(0).getAsInt());
                body[2] = from.get(2).getAsInt();
                body[3] = to.get(2).getAsInt();
                body[4]++;
            }
            for (var face : e.getAsJsonObject("faces").entrySet()) {
                JsonArray uv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                for (JsonElement u : uv)
                    assertEquals(Math.rint(u.getAsDouble()), u.getAsDouble());
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
        assertEquals(3, body[4], "three outlet cells");
        assertArrayEquals(new int[]{StripGeometry.CELL_X[0], StripGeometry.CELL_X[3], StripGeometry.BODY_Z0, StripGeometry.BODY_Z1},
                new int[]{body[0], body[1], body[2], body[3]});
        JsonObject item = ReelAssetsTest.json("assets/voltworks/models/item/power_strip.json");
        assertEquals("voltworks:block/power_strip", item.get("parent").getAsString());
        assertTrue(item.getAsJsonObject("display").has("gui"));
        assertEquals("voltworks:power_strip", ReelAssetsTest.json("data/voltworks/loot_table/blocks/power_strip.json")
                .getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("voltworks:power_strip", ReelAssetsTest.json("data/voltworks/recipe/crafting/power_strip.json")
                .getAsJsonObject("result").get("id").getAsString());
    }

    private static int lum(int argb) {
        return (((argb >> 16) & 255) * 3 + ((argb >> 8) & 255) * 6 + (argb & 255)) / 10;
    }

    @Test
    void textureShowsPinHolesInARoundSocket() throws IOException {
        BufferedImage img;
        try (InputStream in = PowerStripTest.class.getClassLoader().getResourceAsStream("assets/voltworks/textures/block/power_strip.png")) {
            assertNotNull(in);
            img = ImageIO.read(in);
        }
        assertEquals(16, img.getWidth());
        assertEquals(16, img.getHeight());
        int plastic = lum(img.getRGB(0, 2));
        for (int[] pin : new int[][]{{1, 2}, {1, 3}, {4, 2}, {4, 3}})
            assertTrue(lum(img.getRGB(pin[0], pin[1])) < plastic - 50, "pin hole " + pin[0] + "," + pin[1]);
        for (int[] corner : new int[][]{{1, 1}, {4, 1}, {1, 4}, {4, 4}})
            assertEquals(img.getRGB(0, 2), img.getRGB(corner[0], corner[1]), "round socket corner");
        for (int u = 0; u < 14; u++)
            for (int t = 0; t < 4; t++)
                assertEquals(255, img.getRGB(u, t) >>> 24, "opaque " + u + "," + t);
    }

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
        keys.add("entity.voltworks.power_strip");
        for (String lang : new String[]{"en_us", "ru_ru"}) {
            JsonObject l = ReelAssetsTest.json("assets/voltworks/lang/" + lang + ".json");
            for (String k : keys)
                assertTrue(l.has(k), lang + " " + k);
        }
    }
}
