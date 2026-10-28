package io.github.sjzeero.voltworks.cord;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class SocketDevice extends SimpleElectricalDevice {
    public static final double CONTACT_RESISTANCE = 1.0E-3;

    public SocketDevice(Level level, BlockPos pos, DevicesSavedData sd, SimulatedDeviceType<?> type) {
        super(level, pos, sd, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        var b = bridges.builder(pos);
        b.resistor(SocketBlock.POSITIVE, SocketBlock.CORD_POSITIVE, CONTACT_RESISTANCE);
        b.resistor(SocketBlock.NEGATIVE, SocketBlock.CORD_NEGATIVE, CONTACT_RESISTANCE);
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return oldState.getBlock().getClass() != newState.getBlock().getClass();
    }
}
