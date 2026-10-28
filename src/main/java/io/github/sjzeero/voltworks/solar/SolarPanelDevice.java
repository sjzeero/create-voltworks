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
import net.minecraft.world.phys.Vec3;
import io.github.sjzeero.voltworks.Voltworks;

public class SolarPanelDevice extends SimpleElectricalDevice {
    private double lastVoltage;
    private int rayDelay;
    private float sunVisibility = 1;
    private boolean skyVisible = true;
    private double ambient = Double.NaN;
    public double irradiance;

    public SolarPanelDevice(Level level, BlockPos pos, DevicesSavedData sd, SimulatedDeviceType<?> type) {
        super(level, pos, sd, type);
    }

    private double computeIrradiance(BlockState state) {
        double am = SolarPhysics.airMass(level);
        if (am == Double.POSITIVE_INFINITY) return 0;
        double trans = 1 - SolarPhysics.cloudCover(level);
        double irr = SolarPhysics.SOLAR_CONSTANT * Math.pow(0.7, Math.pow(am, 0.678));
        irr = Math.min(SolarPhysics.SOLAR_CONSTANT, irr * (((pos.getY() - 70) / 250f) * 0.04f + 1));
        double a = level.getSunAngle(0);
        Vec3 sun = new Vec3(-Math.sin(a), Math.cos(a), 0);
        if (sun.y <= 0) return 0;
        if (rayDelay-- <= 0) {
            sunVisibility = SolarPhysics.sunVisibility(level, pos);
            skyVisible = SolarPhysics.skyVisible(level, pos);
            rayDelay = level.random.nextInt(41) + 10;
        }
        var nv = state.getValue(SolarPanelBlock.FACING).getOpposite().getNormal();
        Vec3 normal = new Vec3(nv.getX(), nv.getY(), nv.getZ());
        double cos = Math.max(0, sun.dot(normal));
        double diffuse = 0, reflected = 0;
        if (skyVisible) {
            diffuse = SolarPhysics.DIFFUSE_FRAC * sun.y * irr * trans;
            reflected = SolarPhysics.ALBEDO_FRAC * sun.y * irr * trans * ((1 - normal.y) / 2.0);
        }
        return irr * sunVisibility * trans * cos + diffuse + reflected;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        double u = 0, r = 50;
        if (level.isLoaded(pos)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof SolarPanelBlock) {
                if (Double.isNaN(ambient)) ambient = SolarPhysics.ambientTemp(level, pos);
                irradiance = computeIrradiance(state);
                double[] ng = SolarPhysics.ivCurve(irradiance, SolarPhysics.cellTemp(irradiance, ambient), lastVoltage);
                u = ng[0] / ng[1];
                r = 1 / ng[1] + SolarPhysics.RS;
            }
        }
        bridges.builder(pos).voltageSourceWithResistance(0, 1, r, -u);
    }

    @Override
    public void postTick(SimulationResults results) {
        double[] v0 = results.getVoltages(new InWorldNode(0, pos));
        double[] v1 = results.getVoltages(new InWorldNode(1, pos));
        int n = Math.min(v0.length, v1.length);
        if (n > 0) { double dv = v0[n - 1] - v1[n - 1]; lastVoltage = VoltageGuard.isSane(dv) ? dv : 0; }
    }
}
