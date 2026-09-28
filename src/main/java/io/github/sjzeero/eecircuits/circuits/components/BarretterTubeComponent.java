/*
 * Copyright 2025 patryk3211
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
 * Modified by sjzeero: ported from Create: Power Grid to Create: Voltworks.
 */
package io.github.sjzeero.eecircuits.circuits.components;

import com.google.common.collect.ImmutableCollection;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.eecircuits.EECircuits;
import io.github.sjzeero.eecircuits.circuits.circuitboard.ComponentCircuitBuilder;
import io.github.sjzeero.eecircuits.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.eecircuits.circuits.components.properties.FloatProperty;
import io.github.sjzeero.eecircuits.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.eecircuits.circuits.schematic.PlacedComponent;
import io.github.sjzeero.eecircuits.circuits.thermal.ThermalBuilder;
import io.github.sjzeero.eecircuits.circuits.Thermal;
import io.github.sjzeero.eecircuits.pgsim.special.BarretterWire;

public class BarretterTubeComponent extends OrientableComponent {
    public static final FloatProperty MINIMUM_RESISTANCE = new FloatProperty(EECircuits.MOD_ID, "barretter_resistance", 10, 10, 1000);
    public static final FloatProperty HOLDING_CURRENT = new FloatProperty(EECircuits.MOD_ID, "barretter_current", 0.1f, 0.01f, 2);

    public BarretterTubeComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(HOLDING_CURRENT, MINIMUM_RESISTANCE, power(50));
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, @NotNull ThermalBuilder.IEmitter thermals) {
        final var Ih = placed.get(HOLDING_CURRENT);
        final var Rmin = placed.get(MINIMUM_RESISTANCE);
        var tube = new BarretterWire(Ih, Rmin, builder.terminalNode(0), builder.terminalNode(1));
        builder.add(tube);

        placed.add(tube);

        var data = new FloatPair();
        placed.customData = data;

        final var operatingTemperature = 1400f;
        final var dissipationFactor = Thermal.dissipationFactor(50, operatingTemperature);
        thermals.builder()
                .addHeatSource(tube)
                .setThermalMass(0.05f)
                .setOverheatTemperature(1850f)
                .setDissipationFactor(dissipationFactor)
                .withTemperatureCallback(T ->
                    data.current = Mth.clamp((T - 600f) / (1400f - 600f), 0, 1));
    }

    @Override
    public boolean tick(@NotNull PlacedComponent placed) {
        if(!placed.isClient())
            return false;
        renderDataTick(placed);
        return true;
    }
}
