package io.github.sjzeero.voltworks.motor;

/**
 * Electrical &lt;-&gt; stress conversion of the Voltworks motors, matched to Create: Electro Energetics.
 * <p>
 * EE's conversion sheet (conversion.md): {@code 1 W -> 1 su} (and {@code 1 FE/t -> 34 W}). "su" is a stress-unit
 * total, i.e. capacity x RPM, the same quantity Create shows on goggles. EE's own electric motor follows it:
 * <ul>
 *   <li>rated stress = U^2 / R<sub>motor</sub> SU (R<sub>motor</sub> = EE config {@code motorResistance}, 30 ohm),
 *       independent of the RPM it runs at (capacity per RPM = rated / RPM);</li>
 *   <li>the coil seen by the grid is {@code 0.8 * min(6 R, R / load)} with load = network stress / network capacity
 *       clamped to 0.05..1, so the draw is {@code U^2 / (0.8 R) * max(load, 1/6)}: 1.25 W per SU actually used
 *       (80 % efficient) and never less than 1/6 of the full-load draw while idling.</li>
 * </ul>
 * EE's alternator goes the other way at the same 1 SU = 1 W (rotorStressMultiplier = "SUs of 1 Watt" = 1).
 * Pure math, no Minecraft classes, so it is unit tested.
 */
public final class MotorPower {
    /** EE conversion.md: 1 W -> 1 su (EE config rotorStressMultiplier, "SUs of 1 Watt", default 1). */
    public static final double SU_PER_WATT = 1.0;
    /** EE electric motor: the coil is 0.8 x the rated resistance, i.e. 1 SU of load costs 1 / 0.8 = 1.25 W. */
    public static final double EFFICIENCY = 0.8;
    /** EE electric motor: load is clamped to 0.05..1 ... */
    public static final double MIN_LOAD = 0.05;
    /** ... and the coil resistance to at most 6 R, so the idle draw is that of 1/6 load. */
    public static final double IDLE_RESISTANCE_FACTOR = 6.0;
    /** EE config motorResistance default, used when no server config is loaded. */
    public static final double DEFAULT_MOTOR_RESISTANCE = 30.0;

    private MotorPower() {
    }

    /** Mechanical power the motor is rated for at this voltage: U^2 / R, in watts. */
    public static double ratedPower(double voltage, double motorResistance) {
        return voltage * voltage / motorResistance;
    }

    /** Stress (capacity x RPM) the rated power converts to: 1 W -> 1 SU. */
    public static double ratedStress(double voltage, double motorResistance) {
        return ratedPower(voltage, motorResistance) * SU_PER_WATT;
    }

    /** Stress capacity per RPM Create gets when the rated stress is delivered at {@code rpm}. */
    public static double capacityPerRpm(double stress, double rpm) {
        return rpm == 0 ? 0 : Math.abs(stress / rpm);
    }

    public static double clampLoad(double load) {
        if (Double.isNaN(load)) return MIN_LOAD;
        return Math.max(MIN_LOAD, Math.min(1.0, load));
    }

    /** Resistance the coil presents to the grid at this kinetic load (EE: 0.8 * min(6 R, R / load)). */
    public static double coilResistance(double motorResistance, double load) {
        return EFFICIENCY * Math.min(motorResistance * IDLE_RESISTANCE_FACTOR, motorResistance / clampLoad(load));
    }

    /** Electrical power drawn from the grid at this voltage and load: U^2 / coilResistance. */
    public static double inputPower(double voltage, double motorResistance, double load) {
        return voltage * voltage / coilResistance(motorResistance, load);
    }

    /** Rounds a signed stress toward zero to whole {@code step}s (the motor's 64 SU stress steps). */
    public static double quantize(double stress, double step) {
        return (long) (stress / step) * step;
    }

    /** Electrical watts needed per SU of load actually used (above the 1/6 idle floor): 1 / (SU_PER_WATT * 0.8). */
    public static double wattsPerUsedStress() {
        return 1.0 / (SU_PER_WATT * EFFICIENCY);
    }
}
