package io.github.sjzeero.voltworks.cord;

public final class StripGeometry {
    private StripGeometry() {}

    public static final int INPUT = 0;
    public static final int[] OUTLETS = {2, 4, 6};
    private static final double[][] OFFSET = {{-3, 0, -4}, {-3, 0, 4}, {3, 0, -4}};
    private static final boolean[] FLIPPED = {true, true, false};
    public static final double[] PLUG_BACK = {12, 4, 8};
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

    public static double[] plugBack(int supply) {
        int i = index(supply);
        double bx = FLIPPED[i] ? 16 - PLUG_BACK[0] : PLUG_BACK[0];
        double bz = FLIPPED[i] ? 16 - PLUG_BACK[2] : PLUG_BACK[2];
        return new double[]{bx + OFFSET[i][0], PLUG_BACK[1] + OFFSET[i][1], bz + OFFSET[i][2]};
    }

    public static double[] cordDir(int supply) {
        return new double[]{flipped(supply) ? -1 : 1, 0, 0};
    }

    public static double[] node(int id) {
        if (id == INPUT || id == INPUT + 1)
            return INPUT_NODE.clone();
        return plugBack(id & ~1);
    }

    public static double[] slot(int supply) {
        double[] o = OFFSET[index(supply)];
        return new double[]{8 + o[0], 3, 8 + o[2]};
    }
}
