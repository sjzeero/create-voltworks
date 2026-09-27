package io.github.sjzeero.eecircuits.board;

import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardSimTest {
    private static BoardSim sim(ListTag parts, double... u) {
        return new BoardSim(4, BoardParts.read(parts), u);
    }

    private static void tick(BoardSim sim, long serial) {
        sim.ensureStep(serial, 0, 1);
    }

    @Test
    void resistor() {
        ListTag parts = new ListTag();
        parts.add(BoardParts.part("resistor", 100, ResistorComponent.RESISTANCE, 1, 0));
        BoardSim s = sim(parts, 0, 10, 0, 0);
        tick(s, 0);
        assertEquals(0.1, s.wavePinCurrent[0][1], 1e-9);
    }

    @Test
    void diodeForwardAndReverse() {
        ListTag parts = new ListTag();
        parts.add(BoardParts.part("resistor", 100, ResistorComponent.RESISTANCE, 1, 4));
        parts.add(BoardParts.part("diode", 0, null, 0, 4));
        BoardSim s = sim(parts, 0, 5, 0, 0);
        for (int i = 0; i < 20; i++)
            tick(s, i);
        assertTrue(s.converged);
        assertEquals(0.0427792, s.wavePinCurrent[0][1], 1e-6);
        assertEquals(0.722085, s.wavePadVoltage[0][4], 1e-4);
        s.pinU[1] = -5;
        for (int i = 20; i < 40; i++)
            tick(s, i);
        assertEquals(-5.0001, s.wavePadVoltage[0][4], 1e-3);
    }

    @Test
    void rcCharge() {
        ListTag parts = new ListTag();
        parts.add(BoardParts.part("resistor", 1000, ResistorComponent.RESISTANCE, 1, 4));
        parts.add(BoardParts.part("capacitor", 0.001f, CapacitorComponent.CAPACITANCE, 4, 0));
        BoardSim s = sim(parts, 0, 10, 0, 0);
        double[] v = new double[61];
        for (int i = 0; i <= 60; i++) {
            tick(s, i);
            v[i] = s.wavePadVoltage[0][4];
        }
        assertEquals(0.4763, v[0], 1e-3);
        assertEquals(0.9298, v[2], 1e-3);
        assertEquals(6.0422, v[19], 1e-3);
        assertEquals(9.4363, v[59], 1e-3);
    }

    @Test
    void npnSwitch() {
        ListTag parts = new ListTag();
        parts.add(BoardParts.part("resistor", 1000, ResistorComponent.RESISTANCE, 1, 4));
        parts.add(BoardParts.part("npn", 20, BJTComponent.GAIN, 4, 5, 0));
        parts.add(BoardParts.part("resistor", 10000, ResistorComponent.RESISTANCE, 2, 5));
        BoardSim s = sim(parts, 0, 12, 0, 0);
        for (int i = 0; i < 20; i++)
            tick(s, i);
        assertEquals(11.988012, s.wavePadVoltage[0][4], 1e-4);
        s.pinU[2] = 5;
        for (int i = 20; i < 60; i++)
            tick(s, i);
        assertEquals(3.060109, s.wavePadVoltage[0][4], 1e-3);
        assertEquals(0.531585, s.wavePadVoltage[0][5], 1e-4);
    }
}
