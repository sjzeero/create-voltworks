/*
 * Copyright 2026 patryk3211
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
 * Modified by sjzeero: ported from Create: Power Grid to EE Circuits.
 */
package io.github.sjzeero.eecircuits.circuits.components;

import com.google.common.collect.ImmutableCollection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.eecircuits.EECircuits;
import io.github.sjzeero.eecircuits.circuits.circuitboard.ComponentCircuitBuilder;
import io.github.sjzeero.eecircuits.circuits.components.properties.CalculatedProperty;
import io.github.sjzeero.eecircuits.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.eecircuits.circuits.components.properties.FloatProperty;
import io.github.sjzeero.eecircuits.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.eecircuits.circuits.schematic.PlacedComponent;
import io.github.sjzeero.eecircuits.circuits.thermal.ThermalBuilder;
import io.github.sjzeero.eecircuits.circuits.Thermal;
import io.github.sjzeero.eecircuits.pgsim.special.ElectronTubeWire;

public class ElectronTubeComponent extends MirrorableComponent {
    // Koren mu (amplification factor), not small-signal voltage gain
    public static final FloatProperty GAIN = new FloatProperty(EECircuits.MOD_ID, "tube_gain", 5, 1, 100);
    // Doubled from 6800 to preserve operating point with the Koren (1 + sgn(E1)) factor
    public static final FloatProperty K_G = new FloatProperty(EECircuits.MOD_ID, "tube_kg", 13_600, 200, 20_000);
    public static final FloatProperty K_P = new FloatProperty(EECircuits.MOD_ID, "tube_kp", 600, 50, 2000);
    public static final FloatProperty K_VB = new FloatProperty(EECircuits.MOD_ID, "tube_kvb", 300, 10, 1000);
    public static final FloatProperty EX = new FloatProperty(EECircuits.MOD_ID, "tube_ex", 1.5f, 1.2f, 1.6f);
    public static final FloatProperty SATURATION_CURRENT = new FloatProperty(EECircuits.MOD_ID, "tube_saturation_current", 0.1f, 0.001f, 20);
    public static final FloatProperty HEATER_VOLTAGE = new FloatProperty(EECircuits.MOD_ID, "tube_heater_voltage", 6f, 1f, 16f);
    public static final CalculatedProperty<Float> HEATER_POWER = new CalculatedProperty<>(EECircuits.MOD_ID, "tube_heater_power", state -> {
        var Is = state.get(SATURATION_CURRENT);
        return Math.max(5f, Is * 50f);
        }, value -> String.format("%.1f W", value));

    public ElectronTubeComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(GAIN, K_G, K_P, K_VB, EX, SATURATION_CURRENT, HEATER_VOLTAGE, HEATER_POWER);
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, @NotNull ThermalBuilder.IEmitter thermals) {
        final var saturationCurrent = placed.get(SATURATION_CURRENT);
        var tube = new ElectronTubeWire(
                placed.get(GAIN), placed.get(K_G), placed.get(K_P), placed.get(K_VB), placed.get(EX), saturationCurrent,
                builder.terminalNode(0), // Cathode
                builder.terminalNode(2), // Anode
                builder.terminalNode(1)  // Grid
        );
        builder.add(tube);

        var targetPower = placed.get(HEATER_POWER);
        var heaterCurrent = targetPower / placed.get(HEATER_VOLTAGE);
        var heaterResistance = placed.get(HEATER_VOLTAGE) / heaterCurrent;
        var heater = builder.connect(heaterResistance, builder.terminalNode(3), builder.terminalNode(4));

        placed.add(tube);
        placed.add(heater);

        var data = new RenderData();
        placed.customData = data;

        final var operatingTemperature = 1400f;
        final var dissipationFactor = Thermal.dissipationFactor(targetPower, operatingTemperature);
        thermals.builder()
                .addHeatSource(heater)
                .setThermalMass(0.001f * targetPower / 5f)
                .setOverheatTemperature(1600f)
                .setDissipationFactor(dissipationFactor)
                .withTemperatureCallback(temperature -> {
                    tube.setSaturationCurrent(
                            Mth.clamp(temperature - 1300f, 0, 150) * saturationCurrent / 100
                    );
                    data.prev = data.current;
                    data.current = Mth.clamp((temperature - 1000) / 400f, 0, 1.125f);
                });
    }

    @Override
    public void dataFixup(@NotNull CompoundTag tag, int version) {
        if(version == 1) {
            var props = tag.getCompound("Properties");
            // tube_kp present means saved under the full Koren model (or already migrated)
            if (props.contains(K_P.id().toString()))
                return;

            var fromAnodeResistance = props.contains("powergrid:tube_anode_resistance");
            if (fromAnodeResistance) {
                var resistance = props.getFloat("powergrid:tube_anode_resistance");
                var kg = ElectronTubeWire.calculateKg1(
                        1, 0,
                        props.getFloat(GAIN.id().toString()),
                        K_P.defaultValue(),
                        K_VB.defaultValue(),
                        EX.defaultValue(),
                        1 / resistance);
                EECircuits.LOGGER.info("Fixing electron tube anode resistance ({}) into k_g ({})", resistance, kg);
                props.putFloat(K_G.id().toString(), kg);
                props.remove("powergrid:tube_anode_resistance");
            } else if (props.contains(K_G.id().toString())) {
                var kg = props.getFloat(K_G.id().toString());
                EECircuits.LOGGER.info("Migrating electron tube k_g from {} to {} for Koren model", kg, kg * 2);
                props.putFloat(K_G.id().toString(), kg * 2f);
            }

            props.putFloat(K_P.id().toString(), K_P.defaultValue());
            props.putFloat(K_VB.id().toString(), K_VB.defaultValue());
            props.putFloat(EX.id().toString(), EX.defaultValue());
            tag.put("Properties", props);
        }
    }

    public static class RenderData {
        float prev;
        float current;
    }
}
