package io.github.sjzeero.voltworks.reel;

/**
 * Length bookkeeping of a cable reel, kept free of Minecraft classes so it can be unit tested.
 * <p>
 * Electro Energetics measures wire in spools: one spool lays one connection, and a removed connection gives back
 * {@code wireItemsPerSpool} wire items (default 4). A reel stores wire in blocks instead, so it needs a length per
 * spool: {@code spoolLength} blocks (Voltworks config {@code cableReel.spoolLength}, default 16, the copper cord's
 * own longest connection). One wire item is then {@code spoolLength / wireItemsPerSpool} blocks (default 4).
 */
public final class ReelMath {
    private ReelMath() {}

    public static final double EPS = 1.0E-6;

    /** Can one more spool be wound in, given what is inside plus what is pulled out? */
    public static boolean canLoad(double stored, double pulled, double spoolLength, double capacity) {
        return stored + pulled + spoolLength <= capacity + EPS;
    }

    /**
     * Wire used for a connection over {@code distance} blocks: the distance plus a little slack so it sags a bit, never
     * more than is wound in. Negative when the reel does not hold enough wire to reach.
     */
    public static double pullLength(double distance, double stored) {
        if(distance <= 0 || distance > stored + EPS)
            return -1;
        double slack = Math.min(0.5, distance * 0.05);
        return Math.min(stored, distance + slack);
    }

    /** Blocks per loose wire item. */
    public static double blocksPerItem(double spoolLength, int wiresPerSpool) {
        return spoolLength / Math.max(1, wiresPerSpool);
    }

    /** Whole wire items that a length of wire gives back when the reel is broken; any shorter remainder is lost. */
    public static int itemsFor(double blocks, double spoolLength, int wiresPerSpool) {
        if(blocks <= EPS)
            return 0;
        return (int) Math.floor(blocks / blocksPerItem(spoolLength, wiresPerSpool) + EPS);
    }
}
