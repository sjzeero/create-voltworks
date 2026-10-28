/*
 * Copyright 2025-2026 patryk3211
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
package io.github.sjzeero.voltworks.solar;

import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import io.github.sjzeero.voltworks.Voltworks;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class SolarPanelCTBehaviour extends ConnectedTextureBehaviour.Base {
    private static final CTSpriteShiftEntry TOP = CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
            Voltworks.asResource("block/solar_panel/solar_panel"), Voltworks.asResource("block/solar_panel/solar_panel_connected"));
    private static final CTSpriteShiftEntry BOTTOM = CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
            Voltworks.asResource("block/solar_panel/copper_plating"), Voltworks.asResource("block/solar_panel/copper_plating_connected"));

    @Override
    public @Nullable CTSpriteShiftEntry getShift(BlockState state, Direction direction, @Nullable TextureAtlasSprite sprite) {
        Direction facing = state.getValue(SolarPanelBlock.FACING);
        if (facing == direction) return BOTTOM;
        return facing == direction.getOpposite() ? TOP : null;
    }

    @Override
    public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader, BlockPos pos, BlockPos otherPos, Direction face) {
        return state == other;
    }

    @Override
    protected Direction getUpDirection(BlockAndTintGetter reader, BlockPos pos, BlockState state, Direction face) {
        if (face != state.getValue(SolarPanelBlock.FACING)) return super.getUpDirection(reader, pos, state, face);
        return face.getAxis().isHorizontal() ? Direction.DOWN : Direction.NORTH;
    }

    @Override
    protected Direction getRightDirection(BlockAndTintGetter reader, BlockPos pos, BlockState state, Direction face) {
        if (face != state.getValue(SolarPanelBlock.FACING)) return super.getRightDirection(reader, pos, state, face);
        return switch (face.getAxis()) {
            case X -> Direction.NORTH;
            case Y -> Direction.WEST;
            case Z -> Direction.EAST;
        };
    }
}
