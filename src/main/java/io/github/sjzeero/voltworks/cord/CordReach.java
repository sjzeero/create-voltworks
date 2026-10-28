package io.github.sjzeero.voltworks.cord;

import io.github.sjzeero.voltworks.reel.ReelMath;

/** Lengths of a cord being plugged back in, free of Minecraft classes so they can be unit tested. */
public final class CordReach {
    private CordReach() {}

    /** Past this the player holding one end of a cord whose other end is plugged in drops it. */
    public static final double SNAP_SLACK = 6.0;

    /** Can a cord {@code reach} long join two plugs {@code distance} apart? */
    public static boolean fits(double distance, double reach) {
        return distance <= reach + ReelMath.EPS;
    }

    /** Length the plugged cord hangs with: a little slack, never longer than the cord. */
    public static double laidLength(double distance, double reach) {
        return Math.max(0.05, Math.min(reach, distance + ReelMath.slack(distance)));
    }

    /** The cord a player gets back when one end of a laid cord comes out: the length it was made with, else as laid. */
    public static double reachOf(Double remembered, double laid) {
        return remembered != null && remembered >= laid - ReelMath.EPS ? remembered : laid;
    }

    /** The held end slips out of the hand: only when the other end is plugged in and the player went too far. */
    public static boolean snaps(double distance, double reach, boolean otherPlugged) {
        return otherPlugged && distance > reach + SNAP_SLACK;
    }
}
