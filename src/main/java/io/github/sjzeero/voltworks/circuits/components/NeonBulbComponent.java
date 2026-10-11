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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.circuitboard.ComponentCircuitBuilder;
import io.github.sjzeero.voltworks.circuits.components.properties.BooleanProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.CalculatedProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.EnumProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.FloatProperty;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import io.github.sjzeero.voltworks.circuits.thermal.ThermalBuilder;
import io.github.sjzeero.voltworks.pgsim.special.NeonBulbWire;
import io.github.sjzeero.voltworks.circuits.Unit;

import java.util.Collection;
import java.util.List;

public class NeonBulbComponent extends OrientableComponent implements IGoggleLabel {
    public static final FloatProperty BREAKDOWN_VOLTAGE = new FloatProperty(Voltworks.MOD_ID, "neon_tube_vb", 60, 30, 300);
    public static final CalculatedProperty<Float> HOLDING_VOLTAGE = new CalculatedProperty<>(Voltworks.MOD_ID, "neon_tube_vh",
            placed -> 0.75f * placed.get(BREAKDOWN_VOLTAGE),
            v -> Unit.VOLTAGE.formatWithPrefixes(v).string());
    public static final CalculatedProperty<Float> HOLDING_CURRENT = new CalculatedProperty<>(Voltworks.MOD_ID, "neon_tube_ih",
            placed -> 0.2f / placed.get(BREAKDOWN_VOLTAGE),
            v -> Unit.CURRENT.formatWithPrefixes(v).string());
    public static final EnumProperty<DyeColor> COLOR = new EnumProperty<DyeColor>(Voltworks.MOD_ID, "color", DyeColor.class, new DyeColor[]{DyeColor.RED, DyeColor.YELLOW, DyeColor.BLUE, DyeColor.GREEN, DyeColor.WHITE});
    public static final BooleanProperty VERTICAL = new BooleanProperty(Voltworks.MOD_ID, "neon_tube_vertical");
    public static final BooleanProperty LIT = new BooleanProperty(Voltworks.MOD_ID, "lit").hidden().unsafe().cast();

    public NeonBulbComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(BREAKDOWN_VOLTAGE, HOLDING_VOLTAGE, HOLDING_CURRENT, COLOR, VERTICAL, LABEL, LIT, power(1.5f));
    }

    @Override
    public @NotNull ResourceLocation getModelId(@NotNull PlacedComponent component) {
        var id = ComponentRegistry.getId(this);
        return component.get(VERTICAL) ? id.withSuffix("_vertical") : id;
    }

    @Override
    public @NotNull Collection<ResourceLocation> requestedModels() {
        var id = ComponentRegistry.getId(this);
        return List.of(id, id.withSuffix("_vertical"));
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, ThermalBuilder.@NotNull IEmitter thermals) {
        var ih = placed.get(HOLDING_CURRENT);
        var vb = placed.get(BREAKDOWN_VOLTAGE);
        var vh = placed.get(HOLDING_VOLTAGE);

        var wire = new NeonBulbWire(
                vb, vh, ih, 0.005f,
                builder.terminalNode(0), builder.terminalNode(1)
        );
        wire.setLit(placed.get(LIT));
        builder.add(wire);
        placed.add(wire);

        thermals.builder()
                .addHeatSource(wire)
                .setThermalMass(0.01f)
                .setMaxPower(1.5f, 125f);
    }

    @Override
    public boolean tick(@NotNull PlacedComponent placed) {
        if(placed.wires.isEmpty())
            return true;
        var wire = (NeonBulbWire) placed.wires.get(0);
        placed.onServerWorld(() -> $ -> {
            if(wire.isLit() != placed.get(LIT)) {
                placed.set(LIT, wire.isLit());
                placed.notifyClients(LIT);
            }
        });
        return true;
    }

    @Override
    public void stateUpdated(@NotNull PlacedComponent placed) {
        if(placed.wires.isEmpty())
            return;
        var wire = (NeonBulbWire) placed.wires.get(0);
        wire.setLit(placed.get(LIT));
    }
}
