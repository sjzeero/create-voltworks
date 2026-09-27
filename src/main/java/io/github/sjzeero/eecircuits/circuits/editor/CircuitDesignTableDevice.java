package io.github.sjzeero.eecircuits.circuits.editor;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CircuitDesignTableDevice extends SimpleElectricalDevice {
    public static final double RESISTANCE = 480;
    public static final float REQUIRED_POWER = 30;

    private volatile double power;

    public CircuitDesignTableDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    public double power() {
        return power;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        bridges.bridge(new InWorldNode(0, pos), new InWorldNode(1, pos), ElectricalProperties.resistor(RESISTANCE));
    }

    @Override
    public void postTick(SimulationResults results) {
        power = results.getVoltageAtSqr(pos, 0, 1) / RESISTANCE;
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof CircuitDesignTableBlock);
    }

    @Override
    public void read(CompoundTag tag) {
    }

    @Override
    public void write(CompoundTag tag) {
    }
}
