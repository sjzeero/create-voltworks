package io.github.sjzeero.voltworks.battery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BatterySpecTest {
    static final double C = BatterySpec.CAPACITY;

    @Test
    void voltageIsPowerGridsCurveScaledTo300V() {
        double k = 300 / 12.7;
        assertEquals(0, BatterySpec.voltage(0, C));
        assertEquals(11.5 * k, BatterySpec.voltage(1e-9, C), 1e-6);
        assertEquals(271.65, BatterySpec.voltage(1e-9, C), 0.01, "empty");
        assertEquals((11.5 + 1.2 * 0.5) * k, BatterySpec.voltage(C / 2, C), 1e-9);
        assertEquals(300, BatterySpec.voltage(C, C), 1e-9);
        assertEquals(300, BatterySpec.voltage(2 * C, C), 1e-9);
    }

    @Test
    void currentsStayPowerGrids() {
        double k = 300 / 12.7;
        // short circuit of a full block: 127 A as in Power Grid (below the ~150 A that overheats EE's insulated wire)
        assertEquals(127, BatterySpec.voltage(C, C) / BatterySpec.resistance(1, 1), 0.1);
        // a standard EE motor (30 ohm) on one full block: ~9 A, ~2.6 kW, terminals well above the motor's 80 V minimum
        double i = 300 / (30 + BatterySpec.resistance(1, 1));
        assertEquals(9.26, i, 0.01);
        assertTrue(30 * i > 270);
        for (double s = 0.1; s <= 1; s += 0.1)
            assertEquals((Math.exp(-21.18323 * s + 10.58157) + 0.1) * k, BatterySpec.resistance(s, 1), 1e-9);
    }

    @Test
    void resistanceGrowsWhenEmptyAndShrinksWithBankSize() {
        assertEquals((Math.exp(-21.18323 + 10.58157) + 0.1) * 300 / 12.7, BatterySpec.resistance(1, 1), 1e-9);
        assertEquals(BatterySpec.MAX_RESISTANCE, BatterySpec.resistance(0, 1), 1e-9);
        assertEquals(BatterySpec.resistance(0.5, 1) / 4, BatterySpec.resistance(0.5, 4), 1e-12);
        for (double s = 0; s < 1; s += 0.05)
            assertTrue(BatterySpec.resistance(s, 1) >= BatterySpec.resistance(s + 0.05, 1));
    }

    @Test
    void oneKilowattHourPerBlock() {
        assertEquals(1000, BatterySpec.CAPACITY_WH);
        assertEquals(3.6e6, C, 1e-6);
        // far above Electro Energetics' small accumulator (~215 Wh)
        assertTrue(BatterySpec.CAPACITY_WH > 4 * 215);
        // a full 3x3x5 bank: 45 kWh
        assertEquals(45, 45 * BatterySpec.CAPACITY_WH / 1000, 1e-12);
    }

    @Test
    void comparatorAsPowerGrid() {
        assertEquals(0, BatterySpec.comparator(0, C));
        assertEquals(1, BatterySpec.comparator(C * 0.01, C));
        assertEquals(8, BatterySpec.comparator(C * 0.5, C));
        assertEquals(15, BatterySpec.comparator(C, C));
    }

    @Test
    void energyFlow() {
        // discharging into a load: positive power, EMF times current
        assertEquals(12 * 1.0, BatterySpec.energyOut(12, 1, 11), 1e-12);
        // charging from a higher voltage: negative
        assertTrue(BatterySpec.energyOut(12, 1, 13) < 0);
        // an empty battery still charges (Power Grid's rule would leave it at 0 for ever)
        assertEquals(-4.0 * 4.0 / 2.0, BatterySpec.energyOut(0, 2, 4), 1e-12);
        assertEquals(0, BatterySpec.energyOut(0, 2, 0), 1e-12);
    }
}
