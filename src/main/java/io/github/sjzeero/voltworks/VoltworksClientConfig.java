package io.github.sjzeero.voltworks;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class VoltworksClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue HIGH_CONTRAST_TRACES;
    private static final ModConfigSpec.IntValue RED_TOP, GREEN_TOP, BLUE_TOP, ALPHA_TOP, ALPHA_TOP_BEHIND;
    private static final ModConfigSpec.BooleanValue WALL_WIRE_MODE;
    private static final ModConfigSpec.IntValue RED_BOTTOM, GREEN_BOTTOM, BLUE_BOTTOM, ALPHA_BOTTOM, ALPHA_BOTTOM_BEHIND;

    static {
        var b = new ModConfigSpec.Builder();
        b.push("circuitDesignTable");
        HIGH_CONTRAST_TRACES = b.comment("Higher contrast between traces in the circuit editor").define("HighContrastTraces", false);
        RED_TOP = b.comment("Amount of red in traces").defineInRange("traceRedTop", 255, 0, 255);
        GREEN_TOP = b.comment("Amount of green in traces").defineInRange("traceGreenTop", 0, 0, 255);
        BLUE_TOP = b.comment("Amount of blue in traces").defineInRange("traceBlueTop", 0, 0, 255);
        ALPHA_TOP = b.comment("Amount of alpha in traces").defineInRange("traceAlphaTop", 255, 0, 255);
        ALPHA_TOP_BEHIND = b.comment("Amount of alpha in traces").defineInRange("traceAlphaTopBehind", 128, 0, 255);
        RED_BOTTOM = b.comment("Amount of red in traces").defineInRange("traceRedBottom", 0, 0, 255);
        GREEN_BOTTOM = b.comment("Amount of green in traces").defineInRange("traceGreenBottom", 255, 0, 255);
        BLUE_BOTTOM = b.comment("Amount of blue in traces").defineInRange("traceBlueBottom", 0, 0, 255);
        ALPHA_BOTTOM = b.comment("Amount of alpha in traces on the back").defineInRange("traceAlphaBottom", 255, 0, 255);
        ALPHA_BOTTOM_BEHIND = b.comment("Amount of alpha in traces on the back").defineInRange("traceAlphaBottomBehind", 128, 0, 255);
        b.pop();
        b.push("wallWires");
        WALL_WIRE_MODE = b.comment("Wire spools lay wires flat on block faces (WALL mode) instead of hanging them. Toggled in game with the wire mode key.")
                .define("wallMode", false);
        b.pop();
        SPEC = b.build();
    }

    private VoltworksClientConfig() {
    }

    public static boolean wallMode() {
        return SPEC.isLoaded() && WALL_WIRE_MODE.get();
    }

    public static void setWallMode(boolean wall) {
        if(!SPEC.isLoaded())
            return;
        WALL_WIRE_MODE.set(wall);
        WALL_WIRE_MODE.save();
    }

    public static boolean highContrast() {
        return SPEC.isLoaded() && HIGH_CONTRAST_TRACES.get();
    }

    private static int argb(ModConfigSpec.IntValue a, ModConfigSpec.IntValue r, ModConfigSpec.IntValue g, ModConfigSpec.IntValue bl) {
        return (a.get() << 24) | (r.get() << 16) | (g.get() << 8) | bl.get();
    }

    public static int activeFront() {
        return argb(ALPHA_TOP, RED_TOP, GREEN_TOP, BLUE_TOP);
    }

    public static int activeBack() {
        return argb(ALPHA_BOTTOM, RED_BOTTOM, GREEN_BOTTOM, BLUE_BOTTOM);
    }

    public static int front() {
        return argb(ALPHA_TOP_BEHIND, RED_TOP, GREEN_TOP, BLUE_TOP);
    }

    public static int back() {
        return argb(ALPHA_BOTTOM_BEHIND, RED_BOTTOM, GREEN_BOTTOM, BLUE_BOTTOM);
    }
}
