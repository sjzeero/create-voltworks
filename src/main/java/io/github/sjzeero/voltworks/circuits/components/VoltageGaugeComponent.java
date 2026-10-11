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
 * Modified by sjzeero: ported from Create: Power Grid to Voltworks.
 */
package io.github.sjzeero.voltworks.circuits.components;

import com.google.common.collect.ImmutableCollection;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.circuitboard.ComponentCircuitBuilder;
import io.github.sjzeero.voltworks.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.FloatProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.StringProperty;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import io.github.sjzeero.voltworks.circuits.thermal.ThermalBuilder;

public class VoltageGaugeComponent extends GaugeComponent {
    public static final FloatProperty MAX_VOLTAGE = new FloatProperty(Voltworks.MOD_ID, "voltage_gauge_max", 10.0f, 1.0f, 500.0f);
    public static final StringProperty UNIT = new StringProperty(Voltworks.MOD_ID, "gauge_unit", "V");

    public VoltageGaugeComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(UNIT, MAX_VOLTAGE);
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, ThermalBuilder.@NotNull IEmitter thermals) {
        var wire = builder.connect(100_000f, builder.terminalNode(0), builder.terminalNode(1));
        placed.add(wire);
    }

    @Override
    public float getTarget(PlacedComponent placed) {
        if(placed.wires.isEmpty())
            return 0;
        var wire = placed.wires.get(0);
        return Mth.clamp((float) (Math.abs(wire.potentialDifference()) / placed.get(MAX_VOLTAGE)), 0, 1.125f);
    }

    @Override
    public float getValue(PlacedComponent placed) {
        if(placed.wires.isEmpty())
            return 0;
        var wire = placed.wires.get(0);
        return Math.abs((float) wire.potentialDifference());
    }

    @Override
    public float getMaxValue(PlacedComponent placed) {
        return placed.get(MAX_VOLTAGE);
    }

    @Override
    public String getUnit(PlacedComponent placed) {
        return placed.get(UNIT);
    }

    @Override
    public ChatFormatting getColor(float value, float maxValue) {
        if(value < maxValue * 0.01)
            return ChatFormatting.DARK_GRAY;
        else if(value < maxValue * 0.5)
            return ChatFormatting.GREEN;
        else if(value < maxValue * 0.75)
            return ChatFormatting.BLUE;
        return ChatFormatting.LIGHT_PURPLE;
    }
}
