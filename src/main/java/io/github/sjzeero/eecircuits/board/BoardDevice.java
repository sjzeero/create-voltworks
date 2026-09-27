package io.github.sjzeero.eecircuits.board;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class BoardDevice extends SimpleElectricalDevice {
    public static final int PINS = 4;
    public static final int HISTORY = 400;

    private List<PlacedPart> parts = List.of();
    private BoardSim sim;
    private boolean dirty = true;
    private long serial;

    public double[] pinVoltage = new double[PINS];
    public double[] pinCurrent = new double[PINS];
    public double[] padVoltage = new double[0];
    public double[] partCurrent = new double[0];
    public double[][] wavePinCurrent = new double[0][];
    public double[][] wavePadVoltage = new double[0][];
    public boolean converged = true;
    public final Deque<Sample> history = new ArrayDeque<>();

    public record Sample(long tick, double[] pinCurrent, double[] padVoltage, double[] partCurrent) {
    }

    public BoardDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    public List<PlacedPart> parts() {
        return parts;
    }

    public void setParts(ListTag list) {
        parts = BoardParts.read(list);
        dirty = true;
        history.clear();
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        if (dirty || sim == null) {
            sim = new BoardSim(PINS, parts, sim == null ? null : sim.pinU());
            dirty = false;
        }
        sim.serial = ++serial;
        for (int k = 1; k < PINS; k++) {
            if (!sim.used[k])
                continue;
            bridges.bridge(new InWorldNode(k, pos), new InWorldNode(0, pos), new PinBranch(sim, k, 0, serial));
            for (int j = k + 1; j < PINS; j++)
                if (sim.used[j])
                    bridges.bridge(new InWorldNode(k, pos), new InWorldNode(j, pos), new PinBranch(sim, k, j, serial));
        }
    }

    @Override
    public void postTick(SimulationResults results) {
        if (sim == null)
            return;
        for (int k = 0; k < PINS; k++)
            pinVoltage[k] = results.getVoltageAt(new InWorldNode(k, pos), new InWorldNode(0, pos));
        int last = sim.wavePinCurrent.length - 1;
        if (last < 0 || sim.wavePinCurrent[last] == null)
            return;
        pinCurrent = sim.wavePinCurrent[last].clone();
        padVoltage = sim.wavePadVoltage[last].clone();
        partCurrent = sim.wavePartCurrent[last].clone();
        wavePinCurrent = sim.wavePinCurrent.clone();
        wavePadVoltage = sim.wavePadVoltage.clone();
        converged = sim.converged;
        for (PlacedPart part : parts)
            part.component.save(part);
        history.addLast(new Sample(level.getGameTime(), pinCurrent, padVoltage, partCurrent));
        while (history.size() > HISTORY)
            history.removeFirst();
    }

    @Override
    public void read(CompoundTag tag) {
        parts = BoardParts.read(tag.getList("Parts", Tag.TAG_COMPOUND));
        dirty = true;
    }

    @Override
    public void write(CompoundTag tag) {
        tag.put("Parts", BoardParts.write(parts));
    }
}
