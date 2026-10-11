package io.github.sjzeero.voltworks.servo;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Create: Power Grid (patryk3211, Apache-2.0) to Voltworks by sjzeero; electrical side reimplemented on CEE devices.
 */
public class ServoDevice extends SimpleElectricalDevice {
    /** PG: control input is 1000 ohm between the control terminal (2) and - (1). */
    public static final double CONTROL_RESISTANCE = 1000.0;
    private ServoBlockEntity be;

    public ServoDevice(Level level, BlockPos pos, DevicesSavedData sd, SimulatedDeviceType<?> type) {
        super(level, pos, sd, type);
    }

    private ServoBlockEntity be() {
        if (be != null && be.isRemoved()) be = null;
        if (be == null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ServoBlockEntity s) be = s;
        return be;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        var s = be();
        var b = bridges.builder(pos);
        b.resistor(0, 1, s != null ? s.coilResistance() : ServoBlockEntity.IDLE_RESISTANCE);
        b.resistor(2, 1, CONTROL_RESISTANCE);
    }

    @Override
    public void postTick(SimulationResults results) {
        var s = be();
        if (s == null) return;
        double[] v0 = results.getVoltages(new InWorldNode(0, pos));
        double[] v1 = results.getVoltages(new InWorldNode(1, pos));
        double[] v2 = results.getVoltages(new InWorldNode(2, pos));
        int n = Math.min(v0.length, Math.min(v1.length, v2.length));
        for (int i = 0; i < n; i++) {
            s.coilVoltage.add(v0[i] - v1[i]);
            s.controlVoltage.add(v2[i] - v1[i]);
        }
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return oldState.getBlock().getClass() != newState.getBlock().getClass();
    }
}
