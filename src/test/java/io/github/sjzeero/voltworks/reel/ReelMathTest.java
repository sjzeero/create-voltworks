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
}
