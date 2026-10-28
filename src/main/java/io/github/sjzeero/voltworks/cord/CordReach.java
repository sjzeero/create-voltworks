package io.github.sjzeero.voltworks.cord;

import io.github.sjzeero.voltworks.reel.ReelMath;

public final class CordReach {
    private CordReach() {}

    public static final double SNAP_SLACK = 6.0;

    public static boolean fits(double distance, double reach) {
        return distance <= reach + ReelMath.EPS;
    }

    public static double laidLength(double distance, double reach) {
        return Math.max(0.05, Math.min(reach, distance + ReelMath.slack(distance)));
    }

    public static double reachOf(Double remembered, double laid) {
        return remembered != null && remembered >= laid - ReelMath.EPS ? remembered : laid;
    }

    public static boolean snaps(double distance, double reach, boolean otherPlugged) {
        return otherPlugged && distance > reach + SNAP_SLACK;
    }
}
