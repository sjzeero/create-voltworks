package io.github.sjzeero.eecircuits.board;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BoardParts {
    public static final Map<String, BoardComponent> COMPONENTS = Map.of(
            "resistor", new ResistorComponent(),
            "capacitor", new CapacitorComponent(),
            "diode", new DiodeComponent(),
            "npn", new BJTComponent(false),
            "pnp", new BJTComponent(true));

    public static List<PlacedPart> read(ListTag list) {
        List<PlacedPart> parts = new ArrayList<>();
        for (Tag t : list) {
            CompoundTag entry = (CompoundTag) t;
            String type = entry.getString("Type");
            BoardComponent component = COMPONENTS.get(type);
            int[] pads = entry.getIntArray("Pads");
            if (component == null || pads.length != component.padCount())
                continue;
            parts.add(new PlacedPart(type, component, pads, entry.getCompound("Properties").copy()));
        }
        return parts;
    }

    public static ListTag write(List<PlacedPart> parts) {
        ListTag list = new ListTag();
        for (PlacedPart part : parts)
            list.add(part.write());
        return list;
    }

    public static CompoundTag part(String type, float value, String key, int... pads) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type);
        tag.putIntArray("Pads", pads);
        CompoundTag props = new CompoundTag();
        if (key != null)
            props.putFloat(key, value);
        tag.put("Properties", props);
        return tag;
    }
}
