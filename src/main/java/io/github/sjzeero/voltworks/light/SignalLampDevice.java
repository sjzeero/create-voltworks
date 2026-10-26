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

/** A 1 kOhm load between node 0 and node 1; the lamp colour follows the voltage across it. */
public class SignalLampDevice extends SimpleElectricalDevice {
    public static final double RESISTANCE = 1000;

    public SignalLampDevice(Level level, BlockPos pos, DevicesSavedData sd, SimulatedDeviceType<?> type) {
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
        setLevel(v < 2 ? 0 : v < 24 ? 1 : v < 48 ? 2 : 3);
    }

    private void setLevel(int target) {
        var server = level.getServer();
        if (server == null) return;
        server.execute(() -> {
            if (!level.isLoaded(pos)) return;
            BlockState s = level.getBlockState(pos);
            if (s.getBlock() instanceof SignalLampBlock && s.getValue(SignalLampBlock.LEVEL) != target)
                level.setBlock(pos, s.setValue(SignalLampBlock.LEVEL, target), 3);
        });
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof SignalLampBlock);
    }

    @Override
    public void read(CompoundTag tag) {}

    @Override
    public void write(CompoundTag tag) {}
}
