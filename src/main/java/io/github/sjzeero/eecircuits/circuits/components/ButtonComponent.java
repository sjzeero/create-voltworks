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
 * Modified by sjzeero: ported from Create: Power Grid to EE Circuits.
 */
package io.github.sjzeero.eecircuits.circuits.components;

import com.google.common.collect.ImmutableCollection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.eecircuits.EECircuits;
import io.github.sjzeero.eecircuits.circuits.schematic.BoardContext;
import io.github.sjzeero.eecircuits.circuits.circuitboard.ComponentCircuitBuilder;
import io.github.sjzeero.eecircuits.circuits.components.properties.BooleanProperty;
import io.github.sjzeero.eecircuits.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.eecircuits.circuits.components.properties.IntProperty;
import io.github.sjzeero.eecircuits.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.eecircuits.circuits.schematic.PlacedComponent;
import io.github.sjzeero.eecircuits.circuits.thermal.ThermalBuilder;
import io.github.sjzeero.eecircuits.circuits.CircuitSounds;
import io.github.sjzeero.eecircuits.pgsim.SwitchedWire;

import java.util.Collection;
import java.util.List;

public class ButtonComponent extends OrientableComponent implements IInteractableComponent, IGoggleLabel {
    public static final BooleanProperty NC = new BooleanProperty(EECircuits.MOD_ID, "button_nc");
    public static final IntProperty STATE = (IntProperty) new IntProperty(EECircuits.MOD_ID, "button_state", 0, 0, 10).hidden();

    public ButtonComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(STATE, NC, LABEL, current(16));
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, ThermalBuilder.@NotNull IEmitter thermals) {
        var wire = builder.connectSwitch(0.1f, builder.terminalNode(0), builder.terminalNode(1), false);
        placed.add(wire);
        thermals.builder()
                .setMaxCurrent(16, 0.1f, 150)
                .setThermalMass(0.01f)
                .addHeatSource(wire);
    }

    @Override
    public boolean tick(@NotNull PlacedComponent placed) {
        var state = placed.get(STATE);
        if(state > 0) {
            --state;
            placed.set(STATE, state);
            if(state == 0) {
                placed.onServerWorld(() -> world -> CircuitSounds.MICROBUTTON_OFF.playOnServer(world, placed.getPos()));
            }
        }
        if(!placed.wires.isEmpty()) {
            var wire = (SwitchedWire) placed.wires.get(0);
            wire.setState((state == 0) == placed.get(NC));
        }
        return true;
    }

    @Override
    public @NotNull ResourceLocation getModelId(@NotNull PlacedComponent component) {
        if(component.get(STATE) == 0)
            return EECircuits.asResource("button");
        else
            return EECircuits.asResource("button_on");
    }

    @Override
    public @NotNull Collection<ResourceLocation> requestedModels() {
        return List.of(
                EECircuits.asResource("button"),
                EECircuits.asResource("button_on")
        );
    }

    @Override
    public VoxelShape getShape(@NotNull PlacedComponent placed) {
        return IInteractableComponent.extrudedFootprint(placed, 2 / 16f);
    }

    @Override
    public InteractionResult use(BoardContext board, PlacedComponent component, Player player) {
        if(component.get(STATE) == 0) {
            component.onServerWorld(() -> world -> CircuitSounds.MICROBUTTON_ON.playOnServer(world, component.getPos()));
        }
        component.set(STATE, 10);
        component.notifyClients(STATE);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void stateUpdated(@NotNull PlacedComponent placed) {
        super.stateUpdated(placed);
        if (placed.get(STATE) > 0){
        }
    }
}
