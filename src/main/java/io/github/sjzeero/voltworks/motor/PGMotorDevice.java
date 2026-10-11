package io.github.sjzeero.voltworks.motor;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Ported from Create: Power Grid (patryk3211) to Voltworks by sjzeero; electrical side reimplemented on CEE devices.
 * The motor coil as seen by the CEE grid: a resistor between node 0 (+) and node 1 (-).
 */
public class PGMotorDevice extends SimpleElectricalDevice {
    private AbstractPGMotorBlockEntity be;

    public PGMotorDevice(Level level, BlockPos pos, DevicesSavedData sd, SimulatedDeviceType<?> type) {
        super(level, pos, sd, type);
    }

    private AbstractPGMotorBlockEntity be() {
        if (be != null && be.isRemoved()) be = null;
        if (be == null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof AbstractPGMotorBlockEntity m) be = m;
        return be;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        var m = be();
        bridges.builder(pos).resistor(0, 1, m != null ? m.coilResistance() : AbstractPGMotorBlockEntity.COIL_RESISTANCE);
    }

    @Override
    public void postTick(SimulationResults results) {
        var m = be();
        if (m == null) return;
        double[] v0 = results.getVoltages(new InWorldNode(0, pos));
        double[] v1 = results.getVoltages(new InWorldNode(1, pos));
        int n = Math.min(v0.length, v1.length);
        for (int i = 0; i < n; i++) m.voltage.add(v0[i] - v1[i]);
    }
}
