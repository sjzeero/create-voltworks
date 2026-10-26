package io.github.sjzeero.voltworks.light;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Closes node 0 to node 1 with 0.01 ohm while the block state is POWERED; open otherwise. */
public class RedstoneSwitchDevice extends SimpleElectricalDevice {
    public static final double R_CLOSED = 0.01;

    public RedstoneSwitchDevice(Level level, BlockPos pos, DevicesSavedData sd, SimulatedDeviceType<?> type) {
        super(level, pos, sd, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        if (!level.isLoaded(pos)) return;
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof RedstoneSwitchBlock && s.getValue(RedstoneSwitchBlock.POWERED))
            bridges.builder(pos).resistor(0, 1, R_CLOSED);
    }

    @Override
    public void postTick(SimulationResults results) {}

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof RedstoneSwitchBlock);
    }

    @Override
    public void read(CompoundTag tag) {}

    @Override
    public void write(CompoundTag tag) {}
}
