package io.github.sjzeero.voltworks.reel;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CablePathTest {
    static double angle(Vector3d a, Vector3d b) {
        return Math.toDegrees(a.angle(b));
    }

    static Vector3d seg(List<Vector3d> p, int i) {
        return new Vector3d(p.get(i + 1)).sub(p.get(i));
    }

    /** Leaves the model straight along the axis for two segments, then bends without a kink. */
    static void checkShape(List<Vector3d> p, Vector3d to, Vector3d out, String what) {
        int n = p.size();
        assertTrue(n >= 6, what);
        assertEquals(0, p.get(n - 1).distance(to), 1e-9, what + ": ends on the cord pixel");
        Vector3d back = new Vector3d(out).negate();
        // the last two segments are the straight run into the model, along the cord axis
        assertTrue(angle(seg(p, n - 2), back) < 1, what + ": into the model along its axis " + angle(seg(p, n - 2), back));
        assertTrue(angle(seg(p, n - 3), back) < 1, what + ": straight run");
        double run = p.get(n - 3).distance(to);
        assertTrue(run > 0.2 && run <= CablePath.RUN + 1e-9, what + ": run " + run);
        // where the curve meets the run: no kink; and smooth all along
        assertTrue(angle(seg(p, n - 4), seg(p, n - 3)) < 8, what + ": junction " + angle(seg(p, n - 4), seg(p, n - 3)));
        for (int i = 0; i + 2 < n; i++)
            assertTrue(angle(seg(p, i), seg(p, i + 1)) < 15, what + ": bend at " + i + " = " + angle(seg(p, i), seg(p, i + 1)));
    }

    @Test
    void straightOutOfTheModelThenSmoothlyToTheReel() {
        Vector3d from = new Vector3d(3, 2.5, -4);
        for (Vector3d out : new Vector3d[]{new Vector3d(1, 0, 0), new Vector3d(0, -1, 0), new Vector3d(-0.3, 0.2, 0.9).normalize(),
                new Vector3d(0, 0, -1)}) {
            Vector3d to = new Vector3d(0, 1.2, 0);
            List<Vector3d> p = CablePath.points(from, to, out, CablePath.sagDepth(from.distance(to), 1), Double.NEGATIVE_INFINITY);
            assertEquals(0, p.get(0).distance(from), 1e-9);
            checkShape(p, to, out, "out " + out);
        }
    }

    @Test
    void sagsLikeElectroEnergeticsAndKeepsOffTheFloor() {
        Vector3d from = new Vector3d(0, 1, 0), to = new Vector3d(6, 1, 0);
        double depth = CablePath.sagDepth(6, 2);
        List<Vector3d> p = CablePath.points(from, to, new Vector3d(1, 0, 0), depth, Double.NEGATIVE_INFINITY);
        double low = p.stream().mapToDouble(v -> v.y).min().orElseThrow();
        assertEquals(1 - depth, low, depth * 0.4, "hangs about as deep as the parabola");
        List<Vector3d> floored = CablePath.points(from, to, new Vector3d(1, 0, 0), depth, 1);
        assertTrue(floored.stream().allMatch(v -> v.y >= 1 - 1e-9), "not through the floor");
    }

    @Test
    void lyingEndCableLeavesAlongTheModelBack() {
        for (LyingEnd.Lie lie : new LyingEnd.Lie[]{LyingEnd.PLUG, LyingEnd.LUG})
            for (double yaw : new double[]{0, 0.7, 2.5, -1.9}) {
                Matrix4d pose = LyingEnd.pose(lie, yaw);
                Vector3d at = LyingEnd.toEntity(pose, lie.backPx());
                // the model's own axis: two model pixels on it through the drawing matrix
                Vector3d axis = LyingEnd.toEntity(pose, new Vector3d(lie.backDir()).mul(4).add(lie.backPx())).sub(at).normalize();
                Vector3d out = LyingEnd.direction(pose, lie.backDir());
                assertTrue(angle(out, axis) < 0.5);
                assertEquals(0, out.y, 1e-9, "lying: the cord leaves level");
                Vector3d reel = new Vector3d(at).add(new Vector3d(out).mul(5)).add(1.5, 1, -0.5);
                List<Vector3d> p = CablePath.points(reel, at, out, CablePath.sagDepth(reel.distance(at), 1), Math.min(reel.y, at.y));
                Vector3d first = new Vector3d(p.get(p.size() - 2)).sub(at); // first segment out of the model
                assertTrue(angle(first, axis) < 3, lie + " yaw " + yaw + ": " + angle(first, axis));
                checkShape(p, at, out, "lying " + yaw);
            }
    }
}
