package io.github.sjzeero.voltworks.reel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReelMathTest {
    static final double SPOOL = 16, CAP = 64;

    @Test
    void loadsWholeSpoolsUpToCapacity() {
        double stored = 0;
        int loaded = 0;
        while (ReelMath.canLoad(stored, 0, SPOOL, CAP)) {
            stored += SPOOL;
            loaded++;
        }
        assertEquals(4, loaded);
        assertEquals(64, stored);
    }

    @Test
    void pulledWireStillCountsAgainstCapacity() {
        assertFalse(ReelMath.canLoad(40, 20, SPOOL, CAP));
        assertTrue(ReelMath.canLoad(28, 20, SPOOL, CAP));
    }

    @Test
    void pullIsLimitedByWoundWire() {
        assertEquals(-1, ReelMath.pullLength(20, 16));
        assertEquals(-1, ReelMath.pullLength(0, 16));
        double len = ReelMath.pullLength(10, 64);
        assertTrue(len >= 10 && len <= 10.5, "a little slack: " + len);
        assertEquals(16, ReelMath.pullLength(15.9, 16), 1e-9, "never more than is wound");
        assertEquals(16, ReelMath.pullLength(16, 16), 1e-9);
    }

    @Test
    void wireItemConversionMatchesElectroEnergetics() {
        assertEquals(4, ReelMath.blocksPerItem(16, 4), 1e-9);
        assertEquals(16, ReelMath.itemsFor(64, 16, 4));
        assertEquals(4, ReelMath.itemsFor(16, 16, 4), "one spool gives back what a removed wire does");
        assertEquals(2, ReelMath.itemsFor(11.9, 16, 4), "shorter remainder is lost");
        assertEquals(0, ReelMath.itemsFor(0, 16, 4));
    }

    @Test
    void woundPlusPulledIsConserved() {
        double stored = 48, pulled;
        double len = ReelMath.pullLength(23.2, stored);
        stored -= len;
        pulled = len;
        stored += pulled;
        assertEquals(48, stored, 1e-9);
    }

    /** Simulates a Sable structure carrying the far end away and back: wire follows, nothing is created or lost. */
    @Test
    void cableFollowsAMovingEnd() {
        double total = 64, pulled = ReelMath.pullLength(5, total), stored = total - pulled;
        for (double d = 5; d <= 60; d += 0.7) {
            assertFalse(ReelMath.overstretched(d, stored + pulled));
            double want = ReelMath.followLength(d, stored + pulled);
            stored = stored + pulled - want;
            pulled = want;
            assertTrue(pulled >= d - 1e-9 || pulled == total, "never shorter than the distance while wire is left");
            assertTrue(pulled - d <= 0.5 + 1e-9, "only a little slack");
            assertEquals(total, stored + pulled, 1e-9);
        }
        double last = 60;
        for (double d = 60; d >= 2; d -= 1.3) {
            last = d;
            double want = ReelMath.followLength(d, stored + pulled);
            stored = stored + pulled - want;
            pulled = want;
        }
        assertEquals(last + ReelMath.slack(last), pulled, 1e-9, "wound back in when the end comes closer: " + pulled);
        assertEquals(total, stored + pulled, 1e-9);
    }

    @Test
    void cableComesOffOnlyPastAllTheWire() {
        assertEquals(64, ReelMath.followLength(64.6, 64), 1e-9, "everything out");
        assertFalse(ReelMath.overstretched(64.9, 64), "Electro Energetics' one block of give");
        assertTrue(ReelMath.overstretched(65.1, 64));
    }

    @Test
    void fillLevelFollowsTheWireOnTheDrum() {
        double spool = 16, cap = 64;
        assertEquals(0, ReelMath.fillLevel(0, cap), "empty drum");
        for (int spools = 1; spools <= 4; spools++)
            assertEquals(spools, ReelMath.fillLevel(spools * spool, cap), "one level per spool wound in");
        assertEquals(1, ReelMath.fillLevel(0.5, cap), "a little wire still shows");
        assertEquals(2, ReelMath.fillLevel(16.5, cap));
        assertEquals(4, ReelMath.fillLevel(100, cap));
        assertEquals(0, ReelMath.fillLevel(0.01, cap));
        int last = 0;
        for (double s = 0; s <= cap; s += 0.37) {
            int l = ReelMath.fillLevel(s, cap);
            assertTrue(l >= last && l <= ReelMath.FILL_LEVELS);
            last = l;
        }
    }

    @Test
    void lyingEndStaysWithinTheWireOnTheDrum() {
        assertEquals(1.0, ReelMath.reachScale(5, 16), 1e-9);
        assertEquals(1.0, ReelMath.reachScale(16, 16), 1e-9);
        assertEquals(0.5, ReelMath.reachScale(32, 16), 1e-9);
        // pulled back exactly onto full reach
        assertEquals(16.0, 20 * ReelMath.reachScale(20, 16), 1e-9);
        // an empty drum keeps the end at the outlet
        assertEquals(0.0, ReelMath.reachScale(3, 0), 1e-9);
        assertEquals(0.0, ReelMath.reachScale(3, -1), 1e-9);
    }
}
