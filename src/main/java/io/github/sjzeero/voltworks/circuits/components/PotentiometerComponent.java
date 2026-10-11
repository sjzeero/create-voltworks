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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.schematic.BoardContext;
import io.github.sjzeero.voltworks.circuits.circuitboard.ComponentCircuitBuilder;
import io.github.sjzeero.voltworks.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.FloatProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.IntProperty;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import io.github.sjzeero.voltworks.circuits.thermal.ThermalBuilder;
import io.github.sjzeero.voltworks.sim.ElectricWire;


public class PotentiometerComponent extends OrientableComponent implements IInteractableComponent, IGoggleLabel {
    public static final FloatProperty RESISTANCE = new FloatProperty(Voltworks.MOD_ID, "potentiometer_resistance", 1000, 100, 100000);
    public static final IntProperty VALUE = new IntProperty(Voltworks.MOD_ID, "potentiometer_value", 50, 0, 100);


    public PotentiometerComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(RESISTANCE, VALUE, LABEL, power(25));
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, ThermalBuilder.@NotNull IEmitter thermals) {
        var R = placed.get(RESISTANCE);
        // Remap setting to prevent infinite conductance.
        var V = Mth.map(placed.get(VALUE) / 100.0f, 0, 1, 0.01f, 0.99f);
        var wire1 = builder.connect(R * V, builder.terminalNode(0), builder.terminalNode(1));
        var wire2 = builder.connect(R * (1 - V), builder.terminalNode(1), builder.terminalNode(2));

        placed.add(wire1);
        placed.add(wire2);

        thermals.builder()
                .setMaxPower(25, 125)
                .setThermalMass(0.005f)
                .addHeatSource(wire1)
                .addHeatSource(wire2);
    }

    @Override
    public VoxelShape getShape(@NotNull PlacedComponent placed) {
        return IInteractableComponent.extrudedFootprint(placed, 3 / 16f);
    }

    @Override
    public InteractionResult use(BoardContext board, PlacedComponent component, Player player) {
        int step = player.isShiftKeyDown() ? -10 : 10;
        int value = Mth.clamp(component.get(VALUE) + step, 0, 100);
        component.set(VALUE, value);
        component.notifyClients(VALUE);
        stateUpdated(component);
        board.markChanged();
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(value + "%"), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hasClientScreen(PlacedComponent component) {
        return true;
    }

    @Override
    public void stateUpdated(@NotNull PlacedComponent placed) {
        if(placed.wires.isEmpty())
            return;

        var R = placed.get(RESISTANCE);
        // Remap setting to prevent infinite conductance.
        var V = Mth.map(placed.get(VALUE) / 100.0f, 0, 1, 0.01f, 0.99f);
        var wire1 = (ElectricWire) placed.wires.get(0);
        var wire2 = (ElectricWire) placed.wires.get(1);

        wire1.setResistance(R * V);
        wire2.setResistance(R * (1 - V));
    }
}
