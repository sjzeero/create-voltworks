package io.github.sjzeero.voltworks.motor;

public final class MotorPower {
    public static final double SU_PER_WATT = 1.0;
    public static final double EFFICIENCY = 0.8;
    public static final double MIN_LOAD = 0.05;
    public static final double IDLE_RESISTANCE_FACTOR = 6.0;
    public static final double DEFAULT_MOTOR_RESISTANCE = 30.0;

    private MotorPower() {
    }

    public static double ratedPower(double voltage, double motorResistance) {
        return voltage * voltage / motorResistance;
    }

    public static double ratedStress(double voltage, double motorResistance) {
        return ratedPower(voltage, motorResistance) * SU_PER_WATT;
    }

    public static double capacityPerRpm(double stress, double rpm) {
        return rpm == 0 ? 0 : Math.abs(stress / rpm);
    }

    public static double clampLoad(double load) {
        if (Double.isNaN(load)) return MIN_LOAD;
        return Math.max(MIN_LOAD, Math.min(1.0, load));
    }

    public static double coilResistance(double motorResistance, double load) {
        return EFFICIENCY * Math.min(motorResistance * IDLE_RESISTANCE_FACTOR, motorResistance / clampLoad(load));
    }

    public static double inputPower(double voltage, double motorResistance, double load) {
        return voltage * voltage / coilResistance(motorResistance, load);
    }

    public static double quantize(double stress, double step) {
        return (long) (stress / step) * step;
    }

    public static double wattsPerUsedStress() {
        return 1.0 / (SU_PER_WATT * EFFICIENCY);
    }
}
