package io.github.sjzeero.voltworks.motor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The constant speed motor converts like EE's electric motor (EE conversion.md: 1 W -> 1 su). */
class MotorPowerTest {
    static final double R = MotorPower.DEFAULT_MOTOR_RESISTANCE;

    @Test
    void oneWattIsOneStressUnit() {
        assertEquals(1.0, MotorPower.SU_PER_WATT);
        assertEquals(30.0, R, "EE motorResistance default");
        // 300 V on EE's 30 ohm rating -> 3 kW -> 3000 SU, the same as EE's own electric motor (U^2 / R)
        assertEquals(3000, MotorPower.ratedPower(300, R), 1e-9);
        assertEquals(3000, MotorPower.ratedStress(300, R), 1e-9);
        assertEquals(3000, MotorPower.ratedStress(-300, R), 1e-9, "polarity only flips the direction");
        assertEquals(480, MotorPower.ratedStress(120, R), 1e-9);
    }

    @Test
    void stressDoesNotDependOnRpm() {
        double su = MotorPower.ratedStress(300, R);
        for (int rpm : new int[]{16, 32, 64, 128, 256})
            assertEquals(su, MotorPower.capacityPerRpm(su, rpm) * rpm, 1e-9);
        assertEquals(187.5, MotorPower.capacityPerRpm(3000, 16), 1e-9);
        assertEquals(11.71875, MotorPower.capacityPerRpm(3000, 256), 1e-9);
        assertEquals(0, MotorPower.capacityPerRpm(3000, 0));
    }

    @Test
    void drawIsEesMotorCoil() {
        // EE ElectricMotorDevice: 0.8 * min(6 R, R / clamp(load, 0.05, 1))
        assertEquals(24.0, MotorPower.coilResistance(R, 1), 1e-9);
        assertEquals(48.0, MotorPower.coilResistance(R, 0.5), 1e-9);
        assertEquals(144.0, MotorPower.coilResistance(R, 0.05), 1e-9);
        assertEquals(144.0, MotorPower.coilResistance(R, 0), 1e-9);
        assertEquals(144.0, MotorPower.coilResistance(R, Double.NaN), 1e-9);
        assertEquals(24.0, MotorPower.coilResistance(R, 3), 1e-9);
        for (double load = 0; load <= 1.0001; load += 0.05)
            assertEquals(MotorPower.inputPower(300, R, load), MotorPower.inputPower(-300, R, load), 1e-9);
    }

    @Test
    void usedStressCosts125WattsAboveIdle() {
        assertEquals(1.25, MotorPower.wattsPerUsedStress(), 1e-12);
        double rated = MotorPower.ratedStress(300, R);
        for (double load : new double[]{0.2, 0.25, 0.5, 0.75, 1.0})
            assertEquals(rated * load * 1.25, MotorPower.inputPower(300, R, load), 1e-9, "load " + load);
        // idle (and any load under 1/6) draws the 1/6-load power, as EE's motor does
        assertEquals(625, MotorPower.inputPower(300, R, 0), 1e-9);
        assertEquals(625, MotorPower.inputPower(300, R, 0.1), 1e-9);
        assertEquals(3750, MotorPower.inputPower(300, R, 1), 1e-9);
        // efficiency at full load: 3000 SU out of 3750 W in
        assertEquals(0.8, rated / MotorPower.inputPower(300, R, 1), 1e-12);
    }

    @Test
    void quantizedToStressSteps() {
        assertEquals(2944, MotorPower.quantize(3000, 64), 1e-9);
        assertEquals(-2944, MotorPower.quantize(-3000, 64), 1e-9);
        assertEquals(0, MotorPower.quantize(63.9, 64), 1e-9);
        assertEquals(64, MotorPower.quantize(64, 64), 1e-9);
        // 64 SU, the least the motor starts with, needs ~43.8 V on a 30 ohm rating
        assertTrue(MotorPower.ratedStress(43.9, R) >= 64);
        assertTrue(MotorPower.ratedStress(43.8, R) < 64);
    }
}
