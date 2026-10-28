package io.github.sjzeero.voltworks.reel;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of a reel cable between the reel outlet and an end model (held or lying), free of Minecraft classes.
 * <p>
 * The cable leaves the model straight along its cord axis for a short run (two segments), then bends smoothly
 * towards the outlet: a cubic Bezier whose tangent at the end of the run is the cord axis (no kink where run and curve
 * meet) and whose first control point is pulled down so the middle sags about as deep as Electro Energetics'
 * parabola for the same wire ({@code 0.05 * distance * dip}). The bending arm at the model end is short, so a cord
 * axis pointing down (or up) only turns the cable near the model instead of sending it metres below the ground.
 * Like a real hanging cable, no point after the run is higher than the higher of its two ends, and none is below
 * the ground under it.
 */
public final class CablePath {
    private CablePath() {}

    /** Straight run out of a lying model, blocks. */
    public static final double RUN = 0.4;
    /** Straight run out of a model in the hand: short, the hand is close to the eye. */
    public static final double HELD_RUN = 0.18;
    /** Longest bending arm along the cord axis at the model end, blocks. */
    public static final double ARM = 0.6;
    /** Half the thickness of the wire model: its centre stays this far above the ground. */
    public static final double RADIUS = 1 / 16.0;
    /** Bezier sample spacing, blocks. */
    public static final double STEP = 0.25;
    /** Largest turn between two Bezier segments, radians. */
    static final double TURN = Math.toRadians(6);

    /** The lowest a wire centre may be at a point (ground top + {@link #RADIUS}, or -infinity). */
    @FunctionalInterface
    public interface Floor {
        double lowest(double x, double y, double z);

        static Floor flat(double y) {
            return (px, py, pz) -> y;
        }
    }

    /** Depth of Electro Energetics' parabola at the middle of a span, for its dip (sag) value. */
    public static double sagDepth(double distance, double dip) {
        return 0.05 * distance * dip;
    }

    /** No outlet direction (the curve starts along the chord), {@link #RUN} out of the model. */
    public static List<Vector3d> points(Vector3dc from, Vector3dc to, Vector3dc out, double depth, double floor) {
        return points(from, new Vector3d(), 0, to, out, RUN, depth, Floor.flat(floor));
    }

    /** No outlet direction (the curve starts along the chord). */
    public static List<Vector3d> points(Vector3dc from, Vector3dc to, Vector3dc out, double run, double depth, Floor floor) {
        return points(from, new Vector3d(), 0, to, out, run, depth, floor);
    }

    /** A lying end: {@link #RUN} at both ends, nothing lower than {@code floor}. */
    public static List<Vector3d> points(Vector3dc from, Vector3dc fromOut, Vector3dc to, Vector3dc out, double depth, double floor) {
        return points(from, fromOut, RUN, to, out, RUN, depth, Floor.flat(floor));
    }

    /**
     * Points from {@code from} (the reel outlet) to {@code to} (the cord pixel of the model), both included.
     * <p>
     * Straight out of the outlet along {@code fromOut} for {@code fromRun}, straight out of the model along
     * {@code out} for {@code run}, and between the two runs two cubic Bezier halves meeting at the sag point (the
     * middle of the runs' ends, {@code depth} lower, crossing it along the chord), with the run directions as
     * tangents at the run ends: no kink anywhere.
     *
     * @param fromOut out of the outlet (any length; zero = no run there, the curve starts along the chord)
     * @param out     the cord axis at {@code to}, pointing out of the model (any length)
     * @param depth   how deep the middle hangs below the straight line, blocks
     * @param floor   the ground under the cable
     */
    public static List<Vector3d> points(Vector3dc from, Vector3dc fromOut, double fromRun, Vector3dc to, Vector3dc out, double run,
                                        double depth, Floor floor) {
        List<Vector3d> pts = new ArrayList<>();
        double d = from.distance(to);
        Vector3d dir = new Vector3d(out), dir0 = new Vector3d(fromOut);
        if (d < 0.05 || dir.lengthSquared() < 1e-12) {
            pts.add(new Vector3d(from));
            pts.add(new Vector3d(to));
            return pts;
        }
        dir.normalize();
        boolean outlet = dir0.lengthSquared() > 1e-12;
        if (outlet)
            dir0.normalize();
        run = Math.min(run, 0.35 * d);
        fromRun = outlet ? Math.min(fromRun, 0.35 * d) : 0;
        Vector3d j = new Vector3d(dir).mul(run).add(to); // model end of the curve
        j.y = Math.max(j.y, Math.min(to.y(), floor.lowest(j.x, j.y, j.z)));
        Vector3d j0 = new Vector3d(dir0).mul(fromRun).add(from); // outlet end of the curve
        double span = j.distance(j0);
        Vector3d chord = new Vector3d(j).sub(j0);
        if (chord.lengthSquared() > 1e-12)
            chord.normalize();
        if (!outlet) // level along the chord, like a hanging cable at its support
            dir0.set(chord.x, 0, chord.z);
        if (dir0.lengthSquared() < 1e-12)
            dir0.set(chord);
        dir0.normalize();
        double top = Math.max(j.y, j0.y);
        Vector3d mid = new Vector3d(j0).add(j).mul(0.5).sub(0, depth, 0);
        double arm = Math.min(span / 6, ARM), half = span / 6;
        // a cable leaving the outlet away from the other end swings round in a loop to the side rather than folding
        // back on itself: a longer arm out of the outlet and the turn pushed sideways
        double back = Math.max(0, -dir0.dot(chord));
        Vector3d side = new Vector3d(chord).fma(-dir0.dot(chord), dir0).setComponent(1, 0);
        if (side.lengthSquared() < 1e-6)
            side.set(-dir0.z, 0, dir0.x);
        side.normalize().mul(back * Math.min(span / 4, 1.0));
        double arm0 = Math.min(arm * (1 + back), span / 3);
        mid.add(side); // the low point swings out to the side with the loop
        // outlet half: j0 -> mid, model half: mid -> j
        Vector3d[][] halves = {
                {j0, new Vector3d(dir0).mul(arm0).add(j0), new Vector3d(chord).mul(-half).add(mid), mid},
                {mid, new Vector3d(chord).mul(half).add(mid), new Vector3d(dir).mul(arm).add(j), j}};

        if (fromRun > 0)
            pts.add(new Vector3d(from));
        if (fromRun > 0)
            pts.add(new Vector3d(from).add(j0).mul(0.5)); // the outlet run: two straight segments
        // fine samples, then keep a point every STEP or wherever the curve has turned by TURN since the last one,
        // so tight bends get short segments and long gentle spans few
        int fine = Math.max(32, (int) Math.ceil(span / 0.04));
        Vector3d last = null, lastDir = new Vector3d(dir0), prev = null;
        double since = 0;
        for (int h = 0; h < 2; h++)
            for (int i = h == 0 ? 0 : 1; i <= fine; i++) {
                Vector3d[] b = halves[h];
                double t = (double) i / fine, u = 1 - t;
                boolean end = h == 1 && i == fine;
                Vector3d p = end ? new Vector3d(j)
                        : new Vector3d(b[0]).mul(u * u * u).fma(3 * u * u * t, b[1]).fma(3 * u * t * t, b[2]).fma(t * t * t, b[3]);
                boolean start = h == 0 && i == 0;
                if (!start && !end) {
                    p.y = Math.min(p.y, top);
                    p.y = Math.max(p.y, floor.lowest(p.x, p.y, p.z));
                }
                if (last == null) {
                    pts.add(last = p);
                } else {
                    since += p.distance(prev);
                    Vector3d c = new Vector3d(p).sub(last);
                    boolean turned = lastDir != null && c.lengthSquared() > 1e-12 && c.angle(lastDir) > TURN;
                    if (end || since >= STEP || turned) {
                        if (c.lengthSquared() > 1e-12)
                            lastDir = c.normalize(new Vector3d());
                        pts.add(last = p);
                        since = 0;
                    }
                }
                prev = p;
            }
        pts.add(new Vector3d(j).add(to).mul(0.5)); // the run: two straight segments into the model
        pts.add(new Vector3d(to));
        return pts;
    }
}
