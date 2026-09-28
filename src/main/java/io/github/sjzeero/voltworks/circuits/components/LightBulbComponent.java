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
 * Modified by sjzeero: ported from Create: Power Grid to CEE: Voltworks.
 */
package io.github.sjzeero.voltworks.circuits.components;

import com.google.common.collect.ImmutableCollection;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.circuitboard.ComponentCircuitBuilder;
import io.github.sjzeero.voltworks.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.EnumProperty;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import io.github.sjzeero.voltworks.circuits.thermal.ThermalBuilder;
import io.github.sjzeero.voltworks.pgsim.ElectricWire;

public class LightBulbComponent extends OrientableComponent implements IGoggleLabel {
    
    public static final EnumProperty<DyeColor> COLOR = new EnumProperty<DyeColor>(Voltworks.MOD_ID, "color", DyeColor.class);

    public LightBulbComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(LABEL, COLOR, voltage(12), power(3f));
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, ThermalBuilder.@NotNull IEmitter thermals) {
        var wire = new ElectricWire(20f, builder.terminalNode(0), builder.terminalNode(1));
        builder.add(wire);
        placed.add(wire);

        final float R_max = 12 * 12 / 3.0f;
        var data = new FloatPair();
        placed.customData = data;
        thermals.builder()
                .addHeatSource(wire)
                .setThermalMass(0.001f)
                .setMaxPower(3.0f, 1450f)
                .setOverheatTemperature(1850f)
                .withTemperatureCallback(T -> {
                    wire.setResistance(20f + (R_max - 20f) / 1450f * T);
                    var x = Mth.clamp((T - 600f) / (1400f - 600f), 0, 1);
                    data.current = x * x;
                });
    }

    @Override
    public boolean tick(@NotNull PlacedComponent placed) {
        if(!placed.isClient())
            return false;
        renderDataTick(placed);
        return true;
    }
}
