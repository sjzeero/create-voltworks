package io.github.sjzeero.voltworks;

public final class VoltageGuard {
    public static final double MAX_SANE_VOLTAGE = 1.0e4;

    private VoltageGuard() {}

    public static boolean isSane(double v) {
        return Double.isFinite(v) && Math.abs(v) <= MAX_SANE_VOLTAGE;
    }

    public static double powerFromSq(double vSq, double resistance) {
        if (!Double.isFinite(vSq) || vSq < 0 || vSq > MAX_SANE_VOLTAGE * MAX_SANE_VOLTAGE) return 0;
        double p = vSq / resistance;
        return Double.isFinite(p) ? p : 0;
    }
}
