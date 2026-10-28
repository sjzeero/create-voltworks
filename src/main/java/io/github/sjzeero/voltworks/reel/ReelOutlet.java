package io.github.sjzeero.voltworks.reel;

import org.joml.Vector3d;

public final class ReelOutlet {
    private ReelOutlet() {}

    public static final double[] PX = {8, 6, 0};
    public static final double[] PX_SMALL = {8, 5, 1};

    public static double[] px(boolean small) {
        return small ? PX_SMALL : PX;
    }

    public static Vector3d rotated(int quarter, double px, double py, double pz) {
        double x = px / 16, y = py / 16, z = pz / 16;
        return switch (Math.floorMod(quarter, 4)) {
            case 1 -> new Vector3d(1 - z, y, x);
            case 2 -> new Vector3d(1 - x, y, 1 - z);
            case 3 -> new Vector3d(z, y, 1 - x);
            default -> new Vector3d(x, y, z);
        };
    }

    public static Vector3d local(int quarter) {
        return local(quarter, false);
    }

    public static Vector3d local(int quarter, boolean small) {
        double[] p = px(small);
        return rotated(quarter, p[0], p[1], p[2]);
    }

    public static Vector3d dir(int quarter) {
        return switch (Math.floorMod(quarter, 4)) {
            case 1 -> new Vector3d(1, 0, 0);
            case 2 -> new Vector3d(0, 0, 1);
            case 3 -> new Vector3d(-1, 0, 0);
            default -> new Vector3d(0, 0, -1);
        };
    }
}
