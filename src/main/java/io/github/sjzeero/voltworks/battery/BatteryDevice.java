package io.github.sjzeero.voltworks.battery;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import io.github.sjzeero.voltworks.VoltageGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Electrical side of a battery block. The bank's controller is a voltage source with internal resistance
 * ({@link BatterySpec}) between its internal bus nodes 2 (+) and 3 (-); the terminals 0 (+) and 1 (-) of every block
 * of the bank are tied to that bus, so a wire on any block of the bank reaches the whole bank. After Create: Power
 * Grid's battery (patryk3211, Apache-2.0), reimplemented on Electro Energetics devices by sjzeero.
 */
public class BatteryDevice extends SimpleElectricalDevice {
    public static final double CONTACT_RESISTANCE = 1.0E-3;
    public static final int BUS_PLUS = 2, BUS_MINUS = 3;
    /** Seconds one simulation tick stands for. */
    public static final double TICK_SECONDS = 0.05;

    private double sourceVoltage, sourceResistance;
    private boolean source;
    private int displayTimer, syncTimer;
    private int lastComparator = -1;

    public BatteryDevice(Level level, BlockPos pos, DevicesSavedData sd, SimulatedDeviceType<?> type) {
        super(level, pos, sd, type);
    }

    private BatteryBlockEntity battery() {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BatteryBlockEntity be ? be : null;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        source = false;
        BatteryBlockEntity be = battery();
        BlockPos bus = be == null || !be.validBank() ? pos : be.controllerPos();
        bridges.bridge(new InWorldNode(0, pos), new InWorldNode(BUS_PLUS, bus), CONTACT_RESISTANCE);
        bridges.bridge(new InWorldNode(1, pos), new InWorldNode(BUS_MINUS, bus), CONTACT_RESISTANCE);
        if (be == null || !be.isController())
            return;
        double cap = be.capacity();
        sourceVoltage = BatterySpec.voltage(be.energy(), cap);
        sourceResistance = BatterySpec.resistance(cap <= 0 ? 0 : be.energy() / cap, be.size());
        bridges.builder(pos).voltageSourceWithResistance(BUS_PLUS, BUS_MINUS, sourceResistance, -sourceVoltage);
        source = true;
    }

    @Override
    public void postTick(SimulationResults results) {
        if (!source)
            return;
        BatteryBlockEntity be = battery();
        if (be == null || !be.isController())
            return;
        double[] vp = results.getVoltages(new InWorldNode(BUS_PLUS, pos));
        double[] vm = results.getVoltages(new InWorldNode(BUS_MINUS, pos));
        int n = Math.min(vp.length, vm.length);
        double power = 0;
        if (n > 0) {
            for (int i = 0; i < n; i++) {
                double terminal = vp[i] - vm[i];
                if (!VoltageGuard.isSane(terminal))
                    continue;
                power += BatterySpec.energyOut(sourceVoltage, sourceResistance, terminal);
            }
            power /= n;
        }
        double cap = be.capacity();
        double energy = Math.max(0, Math.min(cap, be.energy() - power * TICK_SECONDS));
        be.setEnergyFromSim(energy, power);
        if (++syncTimer >= 20) {
            syncTimer = 0;
            be.syncIfChanged();
        }
        if (++displayTimer >= 10) {
            displayTimer = 0;
            int comparator = BatterySpec.comparator(energy, cap);
            if (comparator != lastComparator) {
                lastComparator = comparator;
                BatteryBanks.updateComparators(level, be);
            }
        }
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
