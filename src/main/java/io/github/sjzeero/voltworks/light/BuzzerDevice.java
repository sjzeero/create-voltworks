package io.github.sjzeero.voltworks.light;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import io.github.sjzeero.voltworks.VoltageGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** A 500 ohm load; beeps every half second while the voltage across it is at least 12 V, pitch rises with voltage. */
public class BuzzerDevice extends SimpleElectricalDevice {
    public static final double RESISTANCE = 500;
    public static final double THRESHOLD = 12;
    private int ticks;

    public BuzzerDevice(Level level, BlockPos pos, DevicesSavedData sd, SimulatedDeviceType<?> type) {
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
        boolean on = v >= THRESHOLD;
        boolean beep = false;
        if (on) {
            if (ticks++ % 10 == 0) beep = true;
        } else {
            ticks = 0;
        }
        final float pitch = (float) Math.max(0.5, Math.min(2.0, 0.5 + v / 48.0));
        final boolean fBeep = beep;
        var server = level.getServer();
        if (server == null) return;
        server.execute(() -> {
            if (!level.isLoaded(pos)) return;
            BlockState s = level.getBlockState(pos);
            if (!(s.getBlock() instanceof BuzzerBlock)) return;
            if (s.getValue(BuzzerBlock.ACTIVE) != on) level.setBlock(pos, s.setValue(BuzzerBlock.ACTIVE, on), 3);
            if (fBeep) level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.8f, pitch);
        });
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof BuzzerBlock);
    }

    @Override
    public void read(CompoundTag tag) {}

    @Override
    public void write(CompoundTag tag) {}
}
