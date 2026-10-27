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

    /** Coil models above the bare drum. */
    public static final int FILL_LEVELS = 4;

    /**
     * Coil model for the wire still wound on the drum (pulled-out wire is not on the drum): 0 when the drum is empty,
     * otherwise 1..{@link #FILL_LEVELS} in proportion to the capacity, rounded up so a little wire still shows. With the
     * default capacity of four spools every spool wound in is one level thicker.
     */
    public static int fillLevel(double stored, double capacity) {
        if (stored <= 0.05)
            return 0;
        if (capacity <= EPS)
            return FILL_LEVELS;
        int level = (int) Math.ceil(stored / capacity * FILL_LEVELS - EPS);
        return Math.max(1, Math.min(FILL_LEVELS, level));
    }

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
        return Math.min(stored, distance + slack(distance));
    }

    /**
     * Electro Energetics snaps a moving wire once the distance is more than a block over its length
     * ({@code WireSimulationState.relocateConnection}); a reel lets everything out first and only then gives up, with
     * the same one block of give.
     */
    public static final double BREAK_GIVE = 1.0;

    /** Slack a reel keeps on a cable it pays out or winds in by itself. */
    public static double slack(double distance) {
        return Math.min(0.5, Math.max(0, distance) * 0.05);
    }

    /** Length a self-retracting cable should have at this distance: distance plus slack, at most all the wire. */
    public static double followLength(double distance, double total) {
        return Math.max(0, Math.min(total, Math.max(0, distance) + slack(distance)));
    }

    /** Distance is past all the wire on the reel (plus Electro Energetics' give): the cable comes off. */
    public static boolean overstretched(double distance, double total) {
        return distance > total + BREAK_GIVE;
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
