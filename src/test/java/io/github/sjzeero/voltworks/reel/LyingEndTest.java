package io.github.sjzeero.voltworks.reel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/** The cable must go exactly into the back of the lying model, and the model must lie on the ground, not in it. */
class LyingEndTest {
    static final double[] YAWS = {0, 0.4, 1.3, Math.PI / 2, 2.2, Math.PI, -0.7, -2.5};

    static double[][] corners(String name) throws IOException {
        JsonObject m = ReelAssetsTest.json("assets/voltworks/models/item/" + name + ".json");
        var els = m.getAsJsonArray("elements");
        double[][] out = new double[els.size() * 8][];
        int k = 0;
        for (JsonElement e : els) {
            var f = e.getAsJsonObject().getAsJsonArray("from");
            var t = e.getAsJsonObject().getAsJsonArray("to");
            for (int i = 0; i < 8; i++)
                out[k++] = new double[]{(i & 1) == 0 ? f.get(0).getAsDouble() : t.get(0).getAsDouble(),
                        (i & 2) == 0 ? f.get(1).getAsDouble() : t.get(1).getAsDouble(),
                        (i & 4) == 0 ? f.get(2).getAsDouble() : t.get(2).getAsDouble()};
        }
        return out;
    }

    /** What the renderer does: pose.mulPose(new Matrix4f(pose)), then ItemRenderer translates by -0.5 and draws pixels / 16. */
    static Vector3d rendered(Matrix4d pose, double[] px) {
        Matrix4f m = new Matrix4f(pose).translate(-0.5f, -0.5f, -0.5f);
        Vector3f v = m.transformPosition(new Vector3f((float) px[0] / 16, (float) px[1] / 16, (float) px[2] / 16));
        return new Vector3d(v.x, v.y, v.z);
    }

    @Test
    void cableMeetsTheBackOfTheModelDrawn() {
        for (LyingEnd.Lie lie : new LyingEnd.Lie[]{LyingEnd.PLUG, LyingEnd.LUG})
            for (double yaw : YAWS) {
                Vector3d attach = LyingEnd.attach(lie, yaw);
                Vector3d drawn = rendered(LyingEnd.pose(lie, yaw), new double[]{lie.backPx().x(), lie.backPx().y(), lie.backPx().z()});
                assertTrue(attach.distance(drawn) < 1e-5, lie + " yaw " + yaw + ": " + attach + " vs " + drawn);
                assertTrue(attach.length() < 0.5, "attach point is on the model, near the entity origin: " + attach);
            }
    }

    @Test
    void modelsLieOnTheGroundNotInIt() throws IOException {
        for (String name : new String[]{"cord_plug", "cable_lug"}) {
            LyingEnd.Lie lie = name.equals("cord_plug") ? LyingEnd.PLUG : LyingEnd.LUG;
            for (double yaw : YAWS) {
                double min = Double.MAX_VALUE;
                for (double[] c : corners(name))
                    min = Math.min(min, rendered(LyingEnd.pose(lie, yaw), c).y);
                assertEquals(0, min, 1e-5, name + " lowest point at yaw " + yaw);
            }
        }
    }

    @Test
    void plugLiesOnItsBackBladesUp() {
        Matrix4d pose = LyingEnd.pose(LyingEnd.PLUG, 0);
        // blades are the elements below the body in the socket model (y 1..3); lying, they must be the highest part
        double bladeMin = Double.MAX_VALUE, bodyMax = -Double.MAX_VALUE;
        for (double[] c : new double[][]{{7, 1, 6}, {9, 1, 10}})
            bladeMin = Math.min(bladeMin, rendered(pose, c).y);
        for (double[] c : new double[][]{{6, 5, 5}, {10, 5, 11}, {6, 3, 5}})
            bodyMax = Math.max(bodyMax, rendered(pose, c).y);
        assertTrue(bladeMin > bodyMax - 1e-6, "blade tips " + bladeMin + " above the body " + bodyMax);
    }

    @Test
    void backFacesTheReel() {
        for (LyingEnd.Lie lie : new LyingEnd.Lie[]{LyingEnd.PLUG, LyingEnd.LUG})
            for (double a : YAWS) {
                double dx = Math.sin(a) * 7, dz = Math.cos(a) * 7; // reel 7 blocks away in direction a
                Vector3d back = LyingEnd.attach(lie, LyingEnd.yaw(lie, dx, dz, 0));
                assertTrue(back.x * dx + back.z * dz > 0, lie + ": back points away from the reel at " + a);
            }
    }
}
