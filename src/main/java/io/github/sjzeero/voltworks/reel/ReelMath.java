package io.github.sjzeero.voltworks.reel;

public final class ReelMath {
    private ReelMath() {}

    public static final double EPS = 1.0E-6;

    public static final int FILL_LEVELS = 4;

    public static int fillLevel(double stored, double capacity) {
        if (stored <= 0.05)
            return 0;
        if (capacity <= EPS)
            return FILL_LEVELS;
        int level = (int) Math.ceil(stored / capacity * FILL_LEVELS - EPS);
        return Math.max(1, Math.min(FILL_LEVELS, level));
    }

    public static boolean canLoad(double stored, double pulled, double spoolLength, double capacity) {
        return stored + pulled + spoolLength <= capacity + EPS;
    }

    public static double pullLength(double distance, double stored) {
        if(distance <= 0 || distance > stored + EPS)
            return -1;
        return Math.min(stored, distance + slack(distance));
    }

    public static final double BREAK_GIVE = 1.0;

    public static double slack(double distance) {
        return Math.min(0.5, Math.max(0, distance) * 0.05);
    }

    public static double followLength(double distance, double total) {
        return Math.max(0, Math.min(total, Math.max(0, distance) + slack(distance)));
    }

    public static boolean overstretched(double distance, double total) {
        return distance > total + BREAK_GIVE;
    }

    public static double blocksPerItem(double spoolLength, int wiresPerSpool) {
        return spoolLength / Math.max(1, wiresPerSpool);
    }

    public static int itemsFor(double blocks, double spoolLength, int wiresPerSpool) {
        if(blocks <= EPS)
            return 0;
        return (int) Math.floor(blocks / blocksPerItem(spoolLength, wiresPerSpool) + EPS);
    }

    public static double reachScale(double distance, double reach) {
        if (distance <= Math.max(reach, 0) + EPS)
            return 1;
        return Math.max(reach, 0) / distance;
    }
}
