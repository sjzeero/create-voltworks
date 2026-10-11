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

/** Ported from Create: Power Grid (patryk3211, Apache-2.0); panels with the same facing connect visually. */
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
