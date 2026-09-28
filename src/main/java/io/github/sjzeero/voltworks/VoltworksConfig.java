package io.github.sjzeero.voltworks;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class VoltworksConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue OVERHEATING;
    private static final ModConfigSpec.DoubleValue HOLDING_CURRENT;
    private static final ModConfigSpec.DoubleValue FAN_COOLING;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        OVERHEATING = b.comment("Board parts heat up and burn when overloaded.").define("overheating", true);
        HOLDING_CURRENT = b.comment("Fraction of the pull-in current that keeps a board relay closed.")
                .defineInRange("holdingCurrentPercent", 0.9, 0.01, 0.99);
        FAN_COOLING = b.comment("Cooling multiplier applied to boards in an encased fan's air stream (multiplied by rotational speed).")
                .defineInRange("encasedFanCooling", 0.01, 0.0, 10.0);
        SPEC = b.build();
    }

    private VoltworksConfig() {
    }

    public static boolean overheating() {
        return !SPEC.isLoaded() || OVERHEATING.get();
    }

    public static float fanCoolingStrength() {
        return SPEC.isLoaded() ? FAN_COOLING.get().floatValue() : 0.01f;
    }

    public static float holdingCurrentPercent() {
        return SPEC.isLoaded() ? HOLDING_CURRENT.get().floatValue() : 0.9f;
    }
}
