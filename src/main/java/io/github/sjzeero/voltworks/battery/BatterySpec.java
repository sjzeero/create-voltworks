package io.github.sjzeero.voltworks.battery;

/**
 * Ported from Create: Power Grid (patryk3211, Apache-2.0; {@code SimpleBatterySpec.ACID_BATTERY} with Power Grid's
 * default config), modified for Voltworks by sjzeero. The electrical model of one battery block, kept free of
 * Minecraft classes so it can be unit tested.
 * <p>
 * A battery is a voltage source with an internal resistance, both depending on the state of charge {@code soc}
 * (0..1): the open-circuit voltage rises from 11.5 V (empty) to 12.7 V (full) and the resistance grows steeply when the
 * battery runs down (a deep-discharged lead-acid cell). Blocks joined into a bank are in parallel: the bank has the
 * same voltage, {@code size} times the capacity and 1/{@code size} of the resistance.
 */
public final class BatterySpec {
    private BatterySpec() {}

    /** Watt-hours one block stores (Power Grid's {@code acidBatteryCapacity}). */
    public static final double CAPACITY_WH = 120;
    /** Joules one block stores. */
    public static final double CAPACITY = CAPACITY_WH * 3600;
    /** Charge of a freshly made battery (Power Grid's {@code acidBatteryInitialCharge}). */
    public static final double INITIAL_SOC = 0.9;
    /** Internal resistance of one full block, ohms (Power Grid's {@code acidBatteryFullChargeInternalResistance}). */
    public static final double FULL_RESISTANCE = 0.1;
    /** Upper limit of one block's internal resistance, ohms. */
    public static final double MAX_RESISTANCE = 10000;

    public static double clampSoc(double soc) {
        return soc < 0 ? 0 : soc > 1 ? 1 : soc;
    }

    /** Open-circuit voltage; 0 when there is nothing left in the battery. */
    public static double voltage(double energy, double capacity) {
        if (energy <= 0 || capacity <= 0)
            return 0;
        return 1.2 * clampSoc(energy / capacity) + 11.5;
    }

    /** Internal resistance of a bank of {@code size} blocks. */
    public static double resistance(double soc, int size) {
        double r = Math.min(MAX_RESISTANCE, Math.exp(-21.18323 * clampSoc(soc) + 10.58157) + FULL_RESISTANCE);
        return r / Math.max(1, size);
    }

    /** Redstone comparator signal of a bank: 0 only when empty, 15 when full (as Power Grid). */
    public static int comparator(double energy, double capacity) {
        double fill = capacity <= 0 ? 0 : clampSoc(energy / capacity);
        return (int) Math.floor(fill * 14) + (fill > 0.001 ? 1 : 0);
    }

    /**
     * Power, W, the stored energy drops by while the terminals sit at {@code terminal} volts: the source's EMF times
     * the current it drives (Power Grid's rule; charging is negative). An empty battery has no EMF, so Power Grid's
     * rule could never charge it again; then the power taken in at the terminals is stored instead.
     */
    public static double energyOut(double emf, double resistance, double terminal) {
        if (resistance <= 0)
            return 0;
        double current = (emf - terminal) / resistance;
        return emf > 0 ? emf * current : terminal * current;
    }
}
