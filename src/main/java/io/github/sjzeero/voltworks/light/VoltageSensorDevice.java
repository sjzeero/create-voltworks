package io.github.sjzeero.voltworks.light;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import io.github.sjzeero.voltworks.VoltageGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** A 10 kOhm load; the redstone output is round(V / 48 * 15), clamped to 0..15. */
public class VoltageSensorDevice extends SimpleElectricalDevice {
    public static final double RESISTANCE = 10000;
    public static final double FULL_SCALE = 48;

    public VoltageSensorDevice(Level level, BlockPos pos, DevicesSavedData sd, SimulatedDeviceType<?> type) {
        super(level, pos, sd, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        bridges.builder(pos).resistor(0, 1, RESISTANCE);
    }

    @Override
    public void postTick(SimulationResults results) {
        double vSq = results.getVoltageAtSqr(pos, 0, 1);
        double v = Double.isFinite(vSq) && vSq >= 0 ? Math.sqrt(vSq) : 0;
        if (!VoltageGuard.isSane(v)) v = 0;
        final int target = (int) Math.max(0, Math.min(15, Math.round(v / FULL_SCALE * 15)));
        var server = level.getServer();
        if (server == null) return;
        server.execute(() -> {
            if (!level.isLoaded(pos)) return;
            BlockState s = level.getBlockState(pos);
            if (s.getBlock() instanceof VoltageSensorBlock && s.getValue(VoltageSensorBlock.POWER) != target)
                level.setBlock(pos, s.setValue(VoltageSensorBlock.POWER, target), 3);
        });
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof VoltageSensorBlock);
    }

    @Override
    public void read(CompoundTag tag) {}

    @Override
    public void write(CompoundTag tag) {}
}
