package io.github.sjzeero.voltworks.cord;

/**
 * Layout of the power strip in its floor model (pixels, before the blockstate rotation), free of Minecraft classes so
 * it can be unit tested. The strip is a 14x14x3 box with three socket outlets in three quarters of its top and its own
 * cord leaving the fourth quarter. Each outlet takes the same cord plug as a socket ({@code cord_plug.json}, built
 * around the socket slot at (8, 8)): the plug model is only moved, never scaled or turned other than the 180 degrees
 * of a socket's B side, so the two outlets on the -X half take plugs whose cord leaves towards -X and the one on the
 * +X half a plug whose cord leaves towards +X - every cord leaves outwards and no plug covers another.
 */
public final class StripGeometry {
    private StripGeometry() {}

    /** Node of the strip's own cord (supply 0, return 1). */
    public static final int INPUT = 0;
    /** Supply contacts of the three outlets (their return contacts are the next odd ids). */
    public static final int[] OUTLETS = {2, 4, 6};
    /** Plug model offset of each outlet from the socket's own slot at (8, 8), in pixels. */
    private static final double[][] OFFSET = {{-3, 0, -4}, {-3, 0, 4}, {3, 0, -4}};
    /** True when the plug in that outlet is turned like a socket's B side (cord towards -X). */
    private static final boolean[] FLIPPED = {true, true, false};
    /** Back of the cord plug model (strain relief end) in socket pixels, A side. */
    public static final double[] PLUG_BACK = {12, 4, 8};
    /** Where the strip's own cord leaves its grommet, and the direction it leaves in. */
    public static final double[] INPUT_NODE = {16, 1.5, 12};
    public static final double[] INPUT_DIR = {1, 0, 0};

    public static int index(int supply) {
        for (int i = 0; i < OUTLETS.length; i++)
            if (OUTLETS[i] == supply)
                return i;
        return -1;
    }

    public static boolean isOutlet(int id) {
        return index(id & ~1) >= 0;
    }

    public static double[] offset(int supply) {
        return OFFSET[index(supply)].clone();
    }

    public static boolean flipped(int supply) {
        return FLIPPED[index(supply)];
    }

    /** Back of the plug sitting in an outlet, in strip floor pixels: where both cord strands end. */
    public static double[] plugBack(int supply) {
        int i = index(supply);
        double bx = FLIPPED[i] ? 16 - PLUG_BACK[0] : PLUG_BACK[0];
        double bz = FLIPPED[i] ? 16 - PLUG_BACK[2] : PLUG_BACK[2];
        return new double[]{bx + OFFSET[i][0], PLUG_BACK[1] + OFFSET[i][1], bz + OFFSET[i][2]};
    }

    /** Direction (floor pixels) the cord leaves the plug in an outlet. */
    public static double[] cordDir(int supply) {
        return new double[]{flipped(supply) ? -1 : 1, 0, 0};
    }

    /** Node position in strip floor pixels: the input grommet for 0/1, an outlet's plug back otherwise. */
    public static double[] node(int id) {
        if (id == INPUT || id == INPUT + 1)
            return INPUT_NODE.clone();
        return plugBack(id & ~1);
    }

    /** The slot centre of an outlet on the strip's top, in floor pixels (for picking the outlet under the cursor). */
    public static double[] slot(int supply) {
        double[] o = OFFSET[index(supply)];
        return new double[]{8 + o[0], 3, 8 + o[2]};
    }
}
