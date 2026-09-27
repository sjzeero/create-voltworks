package io.github.sjzeero.eecircuits.board;

import io.github.sjzeero.eecircuits.pgsim.AbstractElectricWire;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

public class PlacedPart {
    public final String type;
    public final BoardComponent component;
    public final int[] pads;
    public final CompoundTag properties;
    public final List<AbstractElectricWire> wires = new ArrayList<>();

    public PlacedPart(String type, BoardComponent component, int[] pads, CompoundTag properties) {
        this.type = type;
        this.component = component;
        this.pads = pads;
        this.properties = properties;
    }

    public float getFloat(String key, float fallback) {
        return properties.contains(key) ? properties.getFloat(key) : fallback;
    }

    public void add(AbstractElectricWire wire) {
        wires.add(wire);
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type);
        tag.putIntArray("Pads", pads);
        tag.put("Properties", properties.copy());
        return tag;
    }
}
