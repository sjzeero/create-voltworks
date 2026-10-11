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
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.IntProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.StringProperty;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import io.github.sjzeero.voltworks.circuits.components.Expression;
import io.github.sjzeero.voltworks.circuits.components.properties.FloatProperty;

import java.util.List;
import java.util.Optional;

public abstract class GaugeComponent extends OrientableComponent implements IRedstoneComponent, IComponentGoggleInformation {
    public static final IntProperty LEVEL = new IntProperty(Voltworks.MOD_ID, "redstone_level", 0, 0, 15).hidden().unsafe().cast();
    public static final FloatProperty READING = new FloatProperty(Voltworks.MOD_ID, "gauge_reading", 0f, -1e9f, 1e9f).hidden().unsafe().cast();
    public static final StringProperty EQUATION = new StringProperty(Voltworks.MOD_ID, "gauge_equation", "x");

    public GaugeComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(LEVEL, READING, LABEL, EQUATION);
    }

    @Override
    public boolean isEmitter() {
        return true;
    }

    @Override
    public int getEmittedLevel(@NotNull PlacedComponent component) {
        return component.get(LEVEL);
    }

    public abstract float getTarget(PlacedComponent placed);
    public abstract float getValue(PlacedComponent placed);

    public abstract float getMaxValue(PlacedComponent placed);
    public abstract String getUnit(PlacedComponent placed);
    public abstract ChatFormatting getColor(float value, float maxValue);

    @Override
    public boolean tick(@NotNull PlacedComponent placed) {
        var target = getTarget(placed);
        if(!placed.isClient()) {
            float value = getValue(placed);
            float last = placed.get(READING);
            if(Math.abs(value - last) > Math.max(1e-4f, Math.abs(last) * 0.002f)) {
                placed.set(READING, value);
                placed.notifyClients(READING);
            }
        }
        if(getEdge(placed) != null) {
            int redstoneLevel = Mth.clamp((int) (target * 15), 0, 15);
            if (redstoneLevel != placed.get(LEVEL)) {
                placed.set(LEVEL, redstoneLevel);
                IRedstoneComponent.notifyNeighbours(placed);
            }
        }


        return true;
    }

    public float getReading(PlacedComponent placed) {
        return placed.isClient() || placed.wires.isEmpty() ? placed.get(READING) : getValue(placed);
    }

    public Optional<Float> evaluate(PlacedComponent placed) {
        var maxValue = getMaxValue(placed);
        var value = Mth.clamp(getReading(placed), -maxValue, maxValue);
        return Expression.tryParse(placed.get(EQUATION)).map(expr -> expr.eval(value));
    }

    @Override
    public boolean addToGoggleTooltip(PlacedComponent component, List<Component> tooltip, boolean isPlayerSneaking) {
        var label = component.has(LABEL) ? component.get(LABEL) : "";
        tooltip.add(Component.literal("    ").append(label.isEmpty() ? Component.translatable("create.gui.gauge.info_header") : Component.literal(label)));
        var expr = Expression.tryParse(component.get(EQUATION));
        if(expr.isPresent()) {
            float value = getReading(component);
            float maxValue = getMaxValue(component);
            StringBuilder line = new StringBuilder();
            if(Math.abs(value) > maxValue) {
                line.append(value >= 0 ? "> " : "< ");
                value = value < 0 ? -maxValue : maxValue;
            }
            var evaluatedValue = expr.get().eval(value);
            if(evaluatedValue >= 0)
                line.append(" ");
            line.append(String.format("%.2f ", evaluatedValue));
            tooltip.add(Component.literal("    ").append(Component.literal(line.toString())
                    .append(getUnit(component)).withStyle(getColor(Math.abs(value), maxValue))));
        } else {
            tooltip.add(Component.literal("    ").append(Component.translatable("voltworks.gui.invalid_equation").withStyle(ChatFormatting.RED)));
        }
        return true;
    }
}
