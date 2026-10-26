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
    public static final double CONTROL_RESISTANCE = 1000.0;
    public static final double NO_FEEDBACK_CONTROL_RESISTANCE = 1.0e6;
    public static final double NETWORK_COIL_RESISTANCE = 1.0e4;
    private static final double MAX_SANE_VOLTAGE = 1.0e4;
    private static final int ZERO_POTENTIAL_PRIORITY = 1000;
    private ServoBlockEntity be;

    private static boolean isSane(double v) {
        return Double.isFinite(v) && Math.abs(v) <= MAX_SANE_VOLTAGE;
    }

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
        var b = bridges.builder(pos);
        b.resistor(0, 1, NETWORK_COIL_RESISTANCE);
        b.resistor(2, 1, NO_FEEDBACK_CONTROL_RESISTANCE);
        bridges.defaultZeroPotential(new InWorldNode(1, pos), ZERO_POTENTIAL_PRIORITY);
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
            double coil = v0[i] - v1[i];
            double ctrl = v2[i] - v1[i];
            if (!isSane(coil) || !isSane(ctrl)) continue;
            s.coilVoltage.add(coil);
            s.controlVoltage.add(ctrl);
        }
        if (n > 0 && isSane(v0[n - 1]) && isSane(v1[n - 1]) && isSane(v2[n - 1])) {
            s.dbgV0 = v0[n - 1];
            s.dbgV1 = v1[n - 1];
            s.dbgV2 = v2[n - 1];
        }
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return oldState.getBlock().getClass() != newState.getBlock().getClass();
    }
}
