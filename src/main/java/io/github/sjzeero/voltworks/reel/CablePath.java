package io.github.sjzeero.voltworks.reel;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of a reel cable between the reel outlet and an end model (held or lying), free of Minecraft classes.
 * <p>
 * The cable leaves the model straight along its cord axis for a short run (two segments), then bends smoothly
 * towards the outlet: a cubic Bezier whose tangent at the end of the run is the cord axis (so there is no kink where
 * run and curve meet) and whose first control point is pulled down so the middle sags as deep as Electro
 * Energetics' parabola for the same wire ({@code 0.05 * distance * dip}).
 */
public final class CablePath {
    private CablePath() {}

    /** Longest straight run out of the model, blocks. */
    public static final double RUN = 0.4;
    /** Bezier sample spacing, blocks. */
    public static final double STEP = 0.25;
    /** Largest turn between two Bezier segments, radians. */
    static final double TURN = Math.toRadians(6);

    /** Depth of Electro Energetics' parabola at the middle of a span, for its dip (sag) value. */
    public static double sagDepth(double distance, double dip) {
        return 0.05 * distance * dip;
    }

    /**
     * Points from {@code from} (the outlet) to {@code to} (the cord pixel of the model), both included.
     *
     * @param out   the cord axis at {@code to}, pointing out of the model (any length)
     * @param depth how deep the middle hangs below the straight line, blocks
     * @param floor no point lower than this (the end's own height is kept)
     */
    public static List<Vector3d> points(Vector3dc from, Vector3dc to, Vector3dc out, double depth, double floor) {
        List<Vector3d> pts = new ArrayList<>();
        double d = from.distance(to);
        Vector3d dir = new Vector3d(out);
        if (d < 0.05 || dir.lengthSquared() < 1e-12) {
            pts.add(new Vector3d(from));
            pts.add(new Vector3d(to));
            return pts;
        }
        dir.normalize();
        double run = Math.min(RUN, 0.35 * d);
        Vector3d j = new Vector3d(dir).mul(run).add(to); // end of the straight run
        double span = j.distance(from);
        double k = span / 3;
        Vector3d p0 = new Vector3d(from), p3 = j;
        Vector3d p2 = new Vector3d(dir).mul(k).add(j);
        // aimed at p2, not at j: the control polygon stays a plain triangle, so the curve cannot loop or cusp
        // even when the cord axis points away from the reel
        Vector3d p1 = new Vector3d(p2).sub(from).div(3).add(from).sub(0, depth * 8 / 3, 0);
        // fine samples, then keep a point every STEP or wherever the curve has turned by TURN since the last one,
        // so tight bends get short segments and long gentle spans few
        int fine = Math.max(64, (int) Math.ceil(span / 0.02));
        Vector3d last = null, lastDir = null;
        double since = 0;
        Vector3d prev = null;
        for (int i = 0; i <= fine; i++) {
            double t = (double) i / fine, u = 1 - t;
            Vector3d p = new Vector3d(p0).mul(u * u * u).fma(3 * u * u * t, p1).fma(3 * u * t * t, p2).fma(t * t * t, p3);
            if (i > 0 && i < fine && p.y < floor)
                p.y = floor;
            if (last == null) {
                pts.add(last = p);
            } else {
                since += p.distance(prev);
                Vector3d chord = new Vector3d(p).sub(last);
                boolean turned = lastDir != null && chord.lengthSquared() > 1e-12 && chord.angle(lastDir) > TURN;
                if (i == fine || since >= STEP || turned) {
                    if (chord.lengthSquared() > 1e-12)
                        lastDir = chord.normalize(new Vector3d());
                    pts.add(last = p);
                    since = 0;
                }
            }
            prev = p;
        }
        pts.add(new Vector3d(dir).mul(run / 2).add(to));
        pts.add(new Vector3d(to));
        return pts;
    }
}
