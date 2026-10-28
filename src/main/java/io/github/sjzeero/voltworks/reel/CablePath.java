package io.github.sjzeero.voltworks.reel;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

public final class CablePath {
    private CablePath() {}

    public static final double RUN = 0.4;
    public static final double HELD_RUN = 0.18;
    public static final double ARM = 0.6;
    public static final double RADIUS = 1 / 16.0;
    public static final double STEP = 0.25;
    static final double TURN = Math.toRadians(6);

    @FunctionalInterface
    public interface Floor {
        double lowest(double x, double y, double z);

        static Floor flat(double y) {
            return (px, py, pz) -> y;
        }
    }

    public static double sagDepth(double distance, double dip) {
        return 0.05 * distance * dip;
    }

    public static List<Vector3d> points(Vector3dc from, Vector3dc to, Vector3dc out, double depth, double floor) {
        return points(from, new Vector3d(), 0, to, out, RUN, depth, Floor.flat(floor));
    }

    public static List<Vector3d> points(Vector3dc from, Vector3dc to, Vector3dc out, double run, double depth, Floor floor) {
        return points(from, new Vector3d(), 0, to, out, run, depth, floor);
    }

    public static List<Vector3d> points(Vector3dc from, Vector3dc fromOut, Vector3dc to, Vector3dc out, double depth, double floor) {
        return points(from, fromOut, RUN, to, out, RUN, depth, Floor.flat(floor));
    }

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
        Vector3d j = new Vector3d(dir).mul(run).add(to);
        j.y = Math.max(j.y, Math.min(to.y(), floor.lowest(j.x, j.y, j.z)));
        Vector3d j0 = new Vector3d(dir0).mul(fromRun).add(from);
        double span = j.distance(j0);
        Vector3d chord = new Vector3d(j).sub(j0);
        if (chord.lengthSquared() > 1e-12)
            chord.normalize();
        if (!outlet)
            dir0.set(chord.x, 0, chord.z);
        if (dir0.lengthSquared() < 1e-12)
            dir0.set(chord);
        dir0.normalize();
        double top = Math.max(j.y, j0.y);
        Vector3d mid = new Vector3d(j0).add(j).mul(0.5).sub(0, depth, 0);
        double arm = Math.min(span / 6, ARM), half = span / 6;
        double back = Math.max(0, -dir0.dot(chord));
        Vector3d side = new Vector3d(chord).fma(-dir0.dot(chord), dir0).setComponent(1, 0);
        if (side.lengthSquared() < 1e-6)
            side.set(-dir0.z, 0, dir0.x);
        side.normalize().mul(back * Math.min(span / 4, 1.0));
        double arm0 = Math.min(arm * (1 + back), span / 3);
        mid.add(side);
        Vector3d[][] halves = {
                {j0, new Vector3d(dir0).mul(arm0).add(j0), new Vector3d(chord).mul(-half).add(mid), mid},
                {mid, new Vector3d(chord).mul(half).add(mid), new Vector3d(dir).mul(arm).add(j), j}};

        if (fromRun > 0)
            pts.add(new Vector3d(from));
        if (fromRun > 0)
            pts.add(new Vector3d(from).add(j0).mul(0.5));
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
        pts.add(new Vector3d(j).add(to).mul(0.5));
        pts.add(new Vector3d(to));
        return pts;
    }
}
