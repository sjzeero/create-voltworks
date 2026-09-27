package io.github.sjzeero.eecircuits.board;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BoardDevice extends SimpleElectricalDevice {
    public static final int PINS = 2;
    public static final Map<String, BoardComponent> COMPONENTS = Map.of("resistor", new ResistorComponent());

    private final List<PlacedPart> parts = new ArrayList<>();
    private ListTag partsTag = new ListTag();
    public double pinVoltage;
    public double pinCurrent;

    public BoardDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        BoardCircuitBuilder builder = new BoardCircuitBuilder(bridges, pos, i -> i < PINS ? i : BoardCircuitBuilder.INTERNAL_BASE + i, BoardCircuitBuilder.INTERNAL_BASE + 256);
        for (PlacedPart part : parts)
            part.component.bake(part, builder);
    }

    @Override
    public void postTick(SimulationResults results) {
        pinVoltage = results.getVoltageAt(pos, 0, 1);
        pinCurrent = results.getCurrentThrough(pos, 0, 1);
    }

    @Override
    public void read(CompoundTag tag) {
        partsTag = tag.contains("Parts") ? tag.getList("Parts", Tag.TAG_COMPOUND) : defaultParts();
        parts.clear();
        for (Tag t : partsTag) {
            CompoundTag entry = (CompoundTag) t;
            BoardComponent component = COMPONENTS.get(entry.getString("Type"));
            int[] pads = entry.getIntArray("Pads");
            if (component == null || pads.length != component.nodeCount())
                continue;
            parts.add(new PlacedPart(component, pads, entry.getCompound("Properties")));
        }
    }

    public void setParts(ListTag list) {
        CompoundTag tag = new CompoundTag();
        write(tag);
        tag.put("Parts", list);
        read(tag);
    }

    public static CompoundTag resistor(int pad1, int pad2, double resistance) {
        CompoundTag resistor = new CompoundTag();
        resistor.putString("Type", "resistor");
        resistor.putIntArray("Pads", new int[]{pad1, pad2});
        CompoundTag props = new CompoundTag();
        props.putDouble(ResistorComponent.RESISTANCE, resistance);
        resistor.put("Properties", props);
        return resistor;
    }

    @Override
    public void write(CompoundTag tag) {
        tag.put("Parts", partsTag);
    }

    private static ListTag defaultParts() {
        ListTag list = new ListTag();
        list.add(resistor(0, 1, ResistorComponent.DEFAULT));
        return list;
    }
}
