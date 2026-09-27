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

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.eecircuits.circuits.schematic.BoardContext;
import io.github.sjzeero.eecircuits.circuits.schematic.PlacedComponent;

public interface IInteractableComponent {
    VoxelShape getShape(@NotNull PlacedComponent placed);

    InteractionResult use(BoardContext board, PlacedComponent component, Player player);

    default boolean hasClientScreen(PlacedComponent component) {
        return false;
    }

    static VoxelShape extrudedFootprint(@NotNull PlacedComponent placed, float height) {
        var footprint = placed.footprint();
        return Shapes.box(
                placed.x / 16f, Component.BASE_Y, placed.y / 16f,
                (placed.x + footprint.getWidth()) / 16f, Component.BASE_Y + height, (placed.y + footprint.getHeight()) / 16f
        );
    }
}
