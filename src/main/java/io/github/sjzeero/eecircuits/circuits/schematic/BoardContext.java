/*
 * Copyright 2026 sjzeero
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
 */
package io.github.sjzeero.eecircuits.circuits.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public interface BoardContext {
    @Nullable Level level();

    BlockPos pos();

    default boolean isClient() {
        var level = level();
        return level != null && level.isClientSide;
    }

    default void propertyChanged(PlacedComponent component, ResourceLocation property) {
    }

    default void markChanged() {
    }

    default void redstoneChanged(PlacedComponent component) {
    }

    default Vec3 toWorld(Vec3 local) {
        var pos = pos();
        return local.add(pos.getX(), pos.getY(), pos.getZ());
    }
}
