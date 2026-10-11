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
 * Modified by sjzeero: ported from Create: Power Grid to Voltworks.
 */
package io.github.sjzeero.voltworks.circuits.components;

import com.google.common.collect.ImmutableCollection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.circuitboard.ComponentCircuitBuilder;
import io.github.sjzeero.voltworks.circuits.components.properties.CalculatedProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.FloatProperty;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import io.github.sjzeero.voltworks.circuits.thermal.ThermalBuilder;
import io.github.sjzeero.voltworks.circuits.Thermal;
import io.github.sjzeero.voltworks.pgsim.special.PentodeWire;

public class PentodeComponent extends MirrorableComponent {
    public static final FloatProperty GAIN = new FloatProperty(Voltworks.MOD_ID, "tube_gain", 8, 1, 100);
    public static final FloatProperty K_G = new FloatProperty(Voltworks.MOD_ID, "tube_kg", 6_000, 200, 20_000);
    public static final FloatProperty K_G2 = new FloatProperty(Voltworks.MOD_ID, "tube_kg2", 4_500, 200, 20_000);
    public static final FloatProperty K_P = new FloatProperty(Voltworks.MOD_ID, "tube_kp", 48, 10, 2000);
    public static final FloatProperty K_VB = new FloatProperty(Voltworks.MOD_ID, "tube_kvb", 12, 1, 1000);
    public static final FloatProperty EX = new FloatProperty(Voltworks.MOD_ID, "tube_ex", 1.35f, 1.2f, 1.6f);
    public static final FloatProperty SATURATION_CURRENT = new FloatProperty(Voltworks.MOD_ID, "tube_saturation_current", 0.1f, 0.001f, 20);
    public static final FloatProperty HEATER_VOLTAGE = new FloatProperty(Voltworks.MOD_ID, "tube_heater_voltage", 6f, 1f, 16f);
    public static final CalculatedProperty<Float> HEATER_POWER = new CalculatedProperty<>(Voltworks.MOD_ID, "tube_heater_power", state -> {
        var Is = state.get(SATURATION_CURRENT);
        return Math.max(5f, Is * 50f);
    }, value -> String.format("%.1f W", value));

    public PentodeComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(GAIN, K_G, K_G2, K_P, K_VB, EX, SATURATION_CURRENT, HEATER_VOLTAGE, HEATER_POWER);
    }

    @Override
    public void dataFixup(@NotNull CompoundTag tag, int version) {
        var props = tag.getCompound("Properties");
        if (!props.contains(K_G2.id().toString())) {
            props.putFloat(K_G2.id().toString(), K_G2.defaultValue());
            tag.put("Properties", props);
        }
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, @NotNull ThermalBuilder.IEmitter thermals) {
        final var saturationCurrent = placed.get(SATURATION_CURRENT);
        var tube = new PentodeWire(
                placed.get(GAIN), placed.get(K_G), placed.get(K_G2), placed.get(K_P), placed.get(K_VB), placed.get(EX), saturationCurrent,
                builder.terminalNode(0),
                builder.terminalNode(2),
                builder.terminalNode(1),
                builder.terminalNode(5)
        );
        builder.add(tube);

        var targetPower = placed.get(HEATER_POWER);
        var heaterCurrent = targetPower / placed.get(HEATER_VOLTAGE);
        var heaterResistance = placed.get(HEATER_VOLTAGE) / heaterCurrent;
        var heater = builder.connect(heaterResistance, builder.terminalNode(3), builder.terminalNode(4));

        placed.add(tube);
        placed.add(heater);

        var data = new ElectronTubeComponent.RenderData();
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
}
