package io.github.sjzeero.voltworks.reel;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CableReelDevice extends SimpleElectricalDevice {
    public static final double CONTACT_RESISTANCE = 1.0E-3;
    private final boolean cord;

    public CableReelDevice(Level level, BlockPos pos, DevicesSavedData sd, SimulatedDeviceType<?> type, boolean cord) {
        super(level, pos, sd, type);
        this.cord = cord;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        var b = bridges.builder(pos);
        if (cord) {
            b.resistor(0, 2, CONTACT_RESISTANCE);
            b.resistor(1, 3, CONTACT_RESISTANCE);
        } else {
            b.resistor(0, 1, CONTACT_RESISTANCE);
        }
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
