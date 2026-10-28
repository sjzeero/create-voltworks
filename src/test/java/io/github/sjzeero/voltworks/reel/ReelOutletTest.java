package io.github.sjzeero.voltworks.reel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReelOutletTest {
    static final String[] FACINGS = {"north", "east", "south", "west"};

    static double[] arr(JsonObject e, String k) {
        JsonArray a = e.getAsJsonArray(k);
        return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }

    @Test
    void outletIsOnTheGuideFaceForEveryFacingAndFill() throws IOException {
        for (String reel : List.of("cable_reel", "cord_reel", "small_cable_reel", "small_cord_reel")) {
            boolean small = reel.startsWith("small_");
            double[] px = ReelOutlet.px(small);
            JsonObject variants = ReelAssetsTest.json("assets/voltworks/blockstates/" + reel + ".json").getAsJsonObject("variants");
            for (int fill = 0; fill <= ReelMath.FILL_LEVELS; fill++)
                for (int q = 0; q < 4; q++) {
                    JsonObject v = variants.getAsJsonObject("facing=" + FACINGS[q] + ",fill=" + fill);
                    int y = v.has("y") ? v.get("y").getAsInt() : 0;
                    assertEquals(q * 90, y, reel + " " + FACINGS[q] + ": blockstate turn");
                    String model = v.get("model").getAsString().replace("voltworks:", "assets/voltworks/models/") + ".json";
                    double[] from = null, to = null;
                    List<double[][]> bars = new ArrayList<>();
                    for (JsonElement e : ReelAssetsTest.json(model).getAsJsonArray("elements"))
                        if ("outlet".equals(e.getAsJsonObject().get("name").getAsString())) {
                            double[] a = arr(e.getAsJsonObject(), "from"), b = arr(e.getAsJsonObject(), "to");
                            bars.add(new double[][]{a, b});
                            if (from == null) { from = a.clone(); to = b.clone(); }
                            for (int i = 0; i < 3; i++) { from[i] = Math.min(from[i], a[i]); to[i] = Math.max(to[i], b[i]); }
                        }
                    assertNotNull(from, model + ": outlet guide");
                    String what = reel + " " + FACINGS[q] + " fill " + fill;
                    assertEquals(4, bars.size(), what + ": four bars around the hole");
                    for (double[][] b : bars)
                        assertFalse(b[0][0] < px[0] && px[0] < b[1][0] && b[0][1] < px[1] && px[1] < b[1][1], what + ": hole at the cable start");
                    assertEquals(from[2], px[2], 1e-9, what + ": on the outer face");
                    assertEquals((from[0] + to[0]) / 2, px[0], 1e-9, what + ": centred across");
                    assertEquals((from[1] + to[1]) / 2, px[1], 1e-9, what + ": centred in height");
                    Vector3d at = ReelOutlet.local(q, small), out = ReelOutlet.dir(q);
                    Vector3d faceCentre = ReelOutlet.rotated(q, (from[0] + to[0]) / 2, (from[1] + to[1]) / 2, from[2]);
                    assertEquals(0, at.distance(faceCentre), 1e-9, what);
                    Vector3d inside = ReelOutlet.rotated(q, (from[0] + to[0]) / 2, (from[1] + to[1]) / 2, to[2]);
                    assertTrue(new Vector3d(at).sub(inside).normalize().dot(out) > 0.999, what + ": out of the face");
                    assertEquals(0, out.y, 1e-12, what + ": level");
                    assertTrue(at.y < 0.5, what + ": low, at drum height, not at the top");
                }
        }
    }

    @Test
    void cableLeavesTheOutletHorizontallyThenSags() {
        for (boolean small : new boolean[]{false, true})
        for (int q = 0; q < 4; q++) {
            Vector3d reel = new Vector3d(10, 64, -3);
            Vector3d from = ReelOutlet.local(q, small).add(reel), out = ReelOutlet.dir(q);
            for (Vector3d to : new Vector3d[]{new Vector3d(out).mul(6).add(from).add(out.z * 2, 0, out.x * 2).setComponent(1, 64 + CablePath.RADIUS),
                    new Vector3d(out).mul(-4).add(from).add(1.5, 0, 1.5).setComponent(1, 64 + CablePath.RADIUS)}) {
                Vector3d endOut = new Vector3d(from).sub(to).setComponent(1, 0).normalize();
                List<Vector3d> p = CablePath.points(from, out, CablePath.RUN, to, endOut, CablePath.RUN,
                        CablePath.sagDepth(from.distance(to), 1), CablePath.Floor.flat(64 + CablePath.RADIUS));
                String what = (small ? "small " : "big ") + "facing " + q + " to " + to;
                assertEquals(0, p.get(0).distance(from), 1e-9, what);
                Vector3d s0 = new Vector3d(p.get(1)).sub(p.get(0)), s1 = new Vector3d(p.get(2)).sub(p.get(1));
                assertTrue(Math.toDegrees(s0.angle(out)) < 0.5, what + ": first segment straight out of the outlet");
                assertTrue(Math.toDegrees(s1.angle(out)) < 0.5, what + ": second segment too");
                assertEquals(from.y, p.get(2).y, 1e-9, what + ": level run");
                for (int i = 0; i + 2 < p.size(); i++) {
                    Vector3d a = new Vector3d(p.get(i + 1)).sub(p.get(i)), b = new Vector3d(p.get(i + 2)).sub(p.get(i + 1));
                    assertTrue(Math.toDegrees(a.angle(b)) < 15, what + ": bend at " + i + " " + Math.toDegrees(a.angle(b)) + " " + p);
                }
                for (Vector3d v : p) {
                    assertTrue(v.y <= from.y + 1e-9, what + ": never above the outlet " + v);
                    assertTrue(v.y >= 64 + CablePath.RADIUS - 1e-9 || v.equals(p.get(p.size() - 1)), what + ": under ground " + v);
                }
            }
        }
    }
}
