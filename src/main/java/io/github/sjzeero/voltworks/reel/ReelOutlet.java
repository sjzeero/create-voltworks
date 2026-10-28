package io.github.sjzeero.voltworks.reel;

import org.joml.Vector3d;

/**
 * Where the pulled-out cable leaves a reel: the middle of the outer face of the "outlet" guide in the reel block
 * models ({@code outlet [6,2,1]-[10,6,3]} in every fill level, sticking 1 px out of the front of the frame, z = 1 for FACING = NORTH), and
 * the direction it leaves in (straight out of that face, level). Free of Minecraft classes for unit tests.
 * <p>
 * Facing is given as clockwise quarter turns from NORTH (0 N, 1 E, 2 S, 3 W), the same turn the blockstate's
 * {@code y} rotation gives the model.
 */
public final class ReelOutlet {
    private ReelOutlet() {}

    /** The outlet guide's outer face centre, model pixels, FACING = NORTH. */
    public static final double[] PX = {8, 4, 1};

    /** A model pixel position (FACING = NORTH) turned like the blockstate turns the model, in blocks. */
    public static Vector3d rotated(int quarter, double px, double py, double pz) {
        double x = px / 16, y = py / 16, z = pz / 16;
        return switch (Math.floorMod(quarter, 4)) {
            case 1 -> new Vector3d(1 - z, y, x);
            case 2 -> new Vector3d(1 - x, y, 1 - z);
            case 3 -> new Vector3d(z, y, 1 - x);
            default -> new Vector3d(x, y, z);
        };
    }

    /** The outlet point in the block (0..1). */
    public static Vector3d local(int quarter) {
        return rotated(quarter, PX[0], PX[1], PX[2]);
    }

    /** Straight out of the outlet face, level (unit). */
    public static Vector3d dir(int quarter) {
        return switch (Math.floorMod(quarter, 4)) {
            case 1 -> new Vector3d(1, 0, 0);
            case 2 -> new Vector3d(0, 0, 1);
            case 3 -> new Vector3d(-1, 0, 0);
            default -> new Vector3d(0, 0, -1);
        };
    }
}
