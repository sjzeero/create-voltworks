package io.github.sjzeero.voltworks;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class VoltworksConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue OVERHEATING;
    private static final ModConfigSpec.DoubleValue HOLDING_CURRENT;
    private static final ModConfigSpec.DoubleValue FAN_COOLING;
    private static final ModConfigSpec.DoubleValue WALL_WIRE_MAX_LENGTH;
    private static final ModConfigSpec.DoubleValue WALL_WIRE_MAX_LEAD;
    private static final ModConfigSpec.IntValue WALL_WIRE_MAX_POINTS;
    private static final ModConfigSpec.DoubleValue REEL_SPOOL_LENGTH;
    private static final ModConfigSpec.IntValue REEL_CAPACITY_SPOOLS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        OVERHEATING = b.comment("Board parts heat up and burn when overloaded.").define("overheating", true);
        HOLDING_CURRENT = b.comment("Fraction of the pull-in current that keeps a board relay closed.")
                .defineInRange("holdingCurrentPercent", 0.9, 0.01, 0.99);
        FAN_COOLING = b.comment("Cooling multiplier applied to boards in an encased fan's air stream (multiplied by rotational speed).")
                .defineInRange("encasedFanCooling", 0.01, 0.0, 10.0);
        b.push("wallWires");
        WALL_WIRE_MAX_LENGTH = b.comment("Longest wall wire in blocks, measured along its route. The wire type's own limit also applies.")
                .defineInRange("maxLength", 64.0, 1.0, 512.0);
        WALL_WIRE_MAX_LEAD = b.comment("Longest straight piece between a connector and the first or last point on a face.")
                .defineInRange("maxLead", 2.0, 0.5, 8.0);
        WALL_WIRE_MAX_POINTS = b.comment("Most points a player can click for one wall wire.")
                .defineInRange("maxPoints", 64, 1, 512);
        b.pop();
        b.push("cableReel");
        REEL_SPOOL_LENGTH = b.comment("Blocks of wire one spool winds into a cable reel. A loose wire item is this divided by Electro Energetics' wireItemsPerSpool.")
                .defineInRange("spoolLength", 16.0, 1.0, 256.0);
        REEL_CAPACITY_SPOOLS = b.comment("How many spools of wire a cable reel holds.")
                .defineInRange("capacitySpools", 4, 1, 64);
        b.pop();
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

    public static double wallWireMaxLength() {
        return SPEC.isLoaded() ? WALL_WIRE_MAX_LENGTH.get() : 64.0;
    }

    public static double wallWireMaxLead() {
        return SPEC.isLoaded() ? WALL_WIRE_MAX_LEAD.get() : 2.0;
    }

    public static int wallWireMaxPoints() {
        return SPEC.isLoaded() ? WALL_WIRE_MAX_POINTS.get() : 64;
    }

    public static double reelSpoolLength() {
        return SPEC.isLoaded() ? REEL_SPOOL_LENGTH.get() : 16.0;
    }

    public static double reelCapacity() {
        return reelSpoolLength() * (SPEC.isLoaded() ? REEL_CAPACITY_SPOOLS.get() : 4);
    }

    public static float holdingCurrentPercent() {
        return SPEC.isLoaded() ? HOLDING_CURRENT.get().floatValue() : 0.9f;
    }
}
