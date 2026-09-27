package io.github.sjzeero.eecircuits.board;

import net.minecraft.nbt.CompoundTag;

public class PlacedPart {
    public final BoardComponent component;
    public final int[] pads;
    public final CompoundTag properties;

    public PlacedPart(BoardComponent component, int[] pads, CompoundTag properties) {
        this.component = component;
        this.pads = pads;
        this.properties = properties;
    }

    public double getDouble(String key, double fallback) {
        return properties.contains(key) ? properties.getDouble(key) : fallback;
    }
}
