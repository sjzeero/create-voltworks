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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.schematic.BoardContext;
import io.github.sjzeero.voltworks.circuits.circuitboard.ComponentCircuitBuilder;
import io.github.sjzeero.voltworks.circuits.components.properties.BooleanProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import io.github.sjzeero.voltworks.circuits.thermal.ThermalBuilder;
import io.github.sjzeero.voltworks.circuits.CircuitSounds;
import io.github.sjzeero.voltworks.sim.SwitchedWire;

import java.util.Collection;
import java.util.List;

public class SwitchComponent extends OrientableComponent implements IInteractableComponent, IGoggleLabel {
    public static final BooleanProperty STATE = new BooleanProperty(Voltworks.MOD_ID, "switch_state");

    public SwitchComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(STATE, LABEL, current(16));
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, @NotNull ThermalBuilder.IEmitter thermals) {
        var wire = builder.connectSwitch(0.1f, builder.terminalNode(0), builder.terminalNode(1), placed.get(STATE));
        placed.add(wire);
        thermals.builder()
                .setMaxCurrent(16, 0.1f, 150)
                .setThermalMass(0.01f)
                .addHeatSource(wire);
    }

    @Override
    public VoxelShape getShape(@NotNull PlacedComponent placed) {
        return IInteractableComponent.extrudedFootprint(placed, 2 / 16f);
    }

    @Override
    public InteractionResult use(BoardContext board, PlacedComponent placed, Player player) {
        var newState = !placed.get(STATE);
        placed.set(STATE, newState);

        if(!placed.isClient()) {
            placed.onServerWorld(() -> world -> (newState ? CircuitSounds.MICROSWITCH_ON : CircuitSounds.MICROSWITCH_OFF).playOnServer(world, placed.getPos()));
            placed.notifyClients(STATE);
            stateUpdated(placed);
        }
        board.markChanged();
        return InteractionResult.SUCCESS;
    }

    @Override
    public void stateUpdated(@NotNull PlacedComponent placed) {
        super.stateUpdated(placed);
        if(placed.wires.isEmpty())
            return;
        ((SwitchedWire) placed.wires.get(0)).setState(placed.get(STATE));
    }

    @Override
    public @NotNull ResourceLocation getModelId(@NotNull PlacedComponent component) {
        return component.get(STATE)
                ? Voltworks.asResource("switch_on")
                : Voltworks.asResource("switch");
    }

    @Override
    public @NotNull Collection<ResourceLocation> requestedModels() {
        return List.of(
                Voltworks.asResource("switch"),
                Voltworks.asResource("switch_on")
        );
    }
}
