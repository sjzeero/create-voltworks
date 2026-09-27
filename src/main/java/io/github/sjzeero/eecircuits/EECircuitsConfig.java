package io.github.sjzeero.eecircuits;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class EECircuitsConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue OVERHEATING;
    private static final ModConfigSpec.DoubleValue HOLDING_CURRENT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        OVERHEATING = b.comment("Board parts heat up and burn when overloaded.").define("overheating", true);
        HOLDING_CURRENT = b.comment("Fraction of the pull-in current that keeps a board relay closed.")
                .defineInRange("holdingCurrentPercent", 0.9, 0.01, 0.99);
        SPEC = b.build();
    }

    private EECircuitsConfig() {
    }

    public static boolean overheating() {
        return !SPEC.isLoaded() || OVERHEATING.get();
    }

    public static float holdingCurrentPercent() {
        return SPEC.isLoaded() ? HOLDING_CURRENT.get().floatValue() : 0.9f;
    }
}
