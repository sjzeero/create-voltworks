/*
 * Copyright 2025-2026 Create: Power Grid contributors (https://github.com/patryk3211/PowerGrid)
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.schematic.BoardContext;
import io.github.sjzeero.voltworks.circuits.circuitboard.ComponentCircuitBuilder;
import io.github.sjzeero.voltworks.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.EnumProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.FloatProperty;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import io.github.sjzeero.voltworks.circuits.thermal.ThermalBuilder;
import io.github.sjzeero.voltworks.circuits.CircuitSounds;
import io.github.sjzeero.voltworks.Voltworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import io.github.sjzeero.voltworks.pgsim.special.FuseSwitchWire;

import java.util.Collection;
import java.util.List;

public class FuseHolderComponent extends OrientableComponent implements IInteractableComponent, IGoggleLabel {
    public static final TagKey<Item> FUSE_RESETTING = TagKey.create(Registries.ITEM, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Voltworks.MOD_ID, "fuse_resetting"));

    public static final EnumProperty<FuseState> STATE = new EnumProperty<>(Voltworks.MOD_ID, "fuse_state", FuseState.class).hidden().unsafe().cast();
    public static final EnumProperty<FuseState> PREV_STATE = new EnumProperty<>(Voltworks.MOD_ID, "fuse_state_prev", FuseState.class).hidden().unsafe().cast();
    public static final FloatProperty MAX_CURRENT = new FloatProperty(Voltworks.MOD_ID, "current_fuse_max", 10, 1, 20);

    public FuseHolderComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(STATE, PREV_STATE, MAX_CURRENT);
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, @NotNull ThermalBuilder.IEmitter thermals) {
        var fuseWire = new FuseSwitchWire(0.05f, builder.terminalNode(0), builder.terminalNode(1), placed.get(STATE) == FuseState.CLOSED, placed.get(MAX_CURRENT));
        builder.add(fuseWire);
        placed.add(fuseWire);
    }

    @Override
    public VoxelShape getShape(@NotNull PlacedComponent placed) {
        return IInteractableComponent.extrudedFootprint(placed, 3 / 16f);
    }

    @Override
    public boolean tick(@NotNull PlacedComponent placed) {
        if(placed.wires.isEmpty() || placed.isClient())
            return true;

        var fuseWire = (FuseSwitchWire) placed.wires.get(0);
        if (fuseWire.wasBlown()) {
            placed.set(STATE, FuseState.BLOWN);
            placed.notifyClients(STATE);
            stateUpdated(placed);
        }

        return true;
    }

    @Override
    public void stateUpdated(@NotNull PlacedComponent placed) {
        super.stateUpdated(placed);
        if (placed.wires.isEmpty())
            return;
        ((FuseSwitchWire) placed.wires.get(0)).setState(placed.get(STATE) == FuseState.CLOSED);
        placed.set(PREV_STATE, placed.get(STATE));
    }

    public boolean resetFuse(PlacedComponent placed) {
        if (placed.get(STATE) == FuseState.OPEN) {
            placed.set(STATE, FuseState.CLOSED);
            placed.onServerWorld(() -> world -> {
                placed.notifyClients(STATE);
                stateUpdated(placed);
                CircuitSounds.FUSE_INSTALL.playOnServer(world, placed.getPos());
            });
            return true;
        }
        return false;
    }

    public boolean removeBlown(PlacedComponent placed) {
        if(placed.get(STATE) == FuseState.BLOWN) {
            placed.set(STATE, FuseState.OPEN);
            placed.onServerWorld(() -> world -> {
                placed.notifyClients(STATE);
                stateUpdated(placed);
            });
            return true;
        }
        return false;
    }

    @Override
    public InteractionResult use(BoardContext board, PlacedComponent component, Player player) {
        var stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if(stack.is(FUSE_RESETTING)) {
            if(resetFuse(component)) {
                if(!player.isCreative())
                    stack.shrink(1);
                board.markChanged();
                return InteractionResult.SUCCESS;
            }
        } else if(stack.isEmpty()) {
            if(removeBlown(component)) {
                board.markChanged();
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.FAIL;
    }
    
    @Override
    public @NotNull ResourceLocation getModelId(@NotNull PlacedComponent component) {
        return switch (component.get(STATE)) {
            case OPEN -> Voltworks.asResource("fuse");
            case BLOWN -> Voltworks.asResource("fuse_blown");
            case CLOSED -> Voltworks.asResource("fuse_on");
        };
    }

    @Override
    public @NotNull Collection<ResourceLocation> requestedModels() {
        return List.of(
                Voltworks.asResource("fuse"),
                Voltworks.asResource("fuse_blown"),
                Voltworks.asResource("fuse_on")
        );
    }


}
