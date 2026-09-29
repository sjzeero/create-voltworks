package io.github.sjzeero.voltworks.servo;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ServoDevice extends SimpleElectricalDevice {
    public ServoBlockEntity be;

    public ServoDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        if (this.be == null && this.level.isLoaded(this.pos)) {
            BlockEntity blockEntity = this.level.getBlockEntity(this.pos);
            if (blockEntity instanceof ServoBlockEntity sbe) {
                this.be = sbe;
            }
        }
        if (this.be != null) {
            if (this.be.isRemoved()) {
                this.be = null;
            } else {
                double resistance = CEEConfigs.server().resistanceValues.motorResistance.get();
                bridges.builder(this.pos).resistor(0, 1, resistance);
            }
        }
    }

    @Override
    public void postTick(SimulationResults results) {
        if (this.be == null && this.level.isLoaded(this.pos)) {
            BlockEntity blockEntity = this.level.getBlockEntity(this.pos);
            if (blockEntity instanceof ServoBlockEntity sbe) {
                this.be = sbe;
            }
        }
        if (this.be != null) {
            if (this.be.isRemoved()) {
                this.be = null;
            } else {
                double[] v0 = results.getVoltages(new InWorldNode(0, this.pos));
                double[] v1 = results.getVoltages(new InWorldNode(1, this.pos));
                int n = Math.min(v0.length, v1.length);
                for (int i = 0; i < n; ++i) {
                    this.be.averageVoltage.add(v0[i] - v1[i]);
                }
            }
        }
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return oldState.getBlock().getClass() != newState.getBlock().getClass();
    }
}
