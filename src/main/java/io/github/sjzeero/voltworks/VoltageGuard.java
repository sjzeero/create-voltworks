package io.github.sjzeero.voltworks;

/** Shared sanity checks for values coming out of the CEE solver (it can blow up to 1e25 V, Inf or NaN on huge board networks). */
public final class VoltageGuard {
    /** Anything beyond this is a solver blow-up, not a real voltage. */
    public static final double MAX_SANE_VOLTAGE = 1.0e4;

    private VoltageGuard() {}

    public static boolean isSane(double v) {
        return Double.isFinite(v) && Math.abs(v) <= MAX_SANE_VOLTAGE;
    }

    /** Power from a squared voltage; returns 0 for NaN, Inf or an absurd voltage. */
    public static double powerFromSq(double vSq, double resistance) {
        if (!Double.isFinite(vSq) || vSq < 0 || vSq > MAX_SANE_VOLTAGE * MAX_SANE_VOLTAGE) return 0;
        double p = vSq / resistance;
        return Double.isFinite(p) ? p : 0;
    }
}
