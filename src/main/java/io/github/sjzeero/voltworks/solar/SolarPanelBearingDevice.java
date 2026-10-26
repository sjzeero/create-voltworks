package io.github.sjzeero.voltworks.solar;

import io.github.sjzeero.voltworks.VoltageGuard;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import io.github.sjzeero.voltworks.Voltworks;

/**
 * Ported from Create: Power Grid (patryk3211, Apache-2.0) to Voltworks by sjzeero; electrical side reimplemented on CEE devices.
 * The whole panel array as one Thevenin source between node 0 (+) and node 1 (-), re-linearised every tick.
 */
public class SolarPanelBearingDevice extends SimpleElectricalDevice {
    private SolarPanelBearingBlockEntity be;

    public SolarPanelBearingDevice(Level level, BlockPos pos, DevicesSavedData sd, SimulatedDeviceType<?> type) {
        super(level, pos, sd, type);
    }

    private SolarPanelBearingBlockEntity be() {
        if (be != null && be.isRemoved()) be = null;
        if (be == null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof SolarPanelBearingBlockEntity b) be = b;
        return be;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        double u = 0, r = 1e5;
        var b = be();
        if (b != null && b.isRunning() && b.irradiance > 0) {
            int s = b.series, p = b.parallel;
            double[] ng = SolarPhysics.ivCurve(b.irradiance, SolarPhysics.cellTemp(b.irradiance, b.ambient), b.lastVoltage, s, p);
            u = ng[0] / ng[1];
            r = 1 / ng[1] + SolarPhysics.RS * s / p;
        }
        bridges.builder(pos).voltageSourceWithResistance(0, 1, r, -u);
    }

    @Override
    public void postTick(SimulationResults results) {
        var b = be();
        if (b == null) return;
        double[] v0 = results.getVoltages(new InWorldNode(0, pos));
        double[] v1 = results.getVoltages(new InWorldNode(1, pos));
        int n = Math.min(v0.length, v1.length);
        if (n > 0) { double dv = v0[n - 1] - v1[n - 1]; b.lastVoltage = VoltageGuard.isSane(dv) ? dv : 0; }
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return oldState.getBlock().getClass() != newState.getBlock().getClass();
    }
}
