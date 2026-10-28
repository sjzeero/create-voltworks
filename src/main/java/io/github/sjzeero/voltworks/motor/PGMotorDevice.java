/*
 * Copyright 2025-2026 patryk3211
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified by sjzeero: ported from Create: Power Grid to Voltworks.
 */
package io.github.sjzeero.voltworks.motor;

import io.github.sjzeero.voltworks.VoltageGuard;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import io.github.sjzeero.voltworks.Voltworks;

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
        bridges.builder(pos).resistor(0, 1, m != null ? m.coilResistance() : MotorPower.coilResistance(AbstractPGMotorBlockEntity.motorResistance(), MotorPower.MIN_LOAD));
    }

    @Override
    public void postTick(SimulationResults results) {
        var m = be();
        if (m == null) return;
        double[] v0 = results.getVoltages(new InWorldNode(0, pos));
        double[] v1 = results.getVoltages(new InWorldNode(1, pos));
        int n = Math.min(v0.length, v1.length);
        for (int i = 0; i < n; i++) {
            double dv = v0[i] - v1[i];
            if (VoltageGuard.isSane(dv)) m.voltage.add(dv);
        }
    }
}
