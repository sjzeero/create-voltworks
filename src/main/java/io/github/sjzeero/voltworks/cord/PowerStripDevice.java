package io.github.sjzeero.voltworks.cord;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class PowerStripDevice extends SimpleElectricalDevice {
    public PowerStripDevice(Level level, BlockPos pos, DevicesSavedData sd, SimulatedDeviceType<?> type) {
        super(level, pos, sd, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        var b = bridges.builder(pos);
        for (int supply : PowerStripBlock.OUTLETS) {
            b.resistor(PowerStripBlock.INPUT, supply, SocketDevice.CONTACT_RESISTANCE);
            b.resistor(PowerStripBlock.INPUT + 1, supply + 1, SocketDevice.CONTACT_RESISTANCE);
        }
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return oldState.getBlock().getClass() != newState.getBlock().getClass();
    }
}
