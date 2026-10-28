package io.github.sjzeero.voltworks.battery;

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

/**
 * Blocks of one bank lose the casing border between them (Create's rectangle CT, as Power Grid's battery and Create's
 * fluid tank). Each gauge level has its own side sheet.
 */
public class BatteryCTBehaviour extends ConnectedTextureBehaviour.Base {
    private static final CTSpriteShiftEntry[] SIDE = new CTSpriteShiftEntry[BatterySpec.GAUGE_LEVELS + 1];
    private static final CTSpriteShiftEntry TOP = CTSpriteShifter.getCT(AllCTTypes.RECTANGLE,
            Voltworks.asResource("block/battery/battery_top"), Voltworks.asResource("block/battery/battery_top_connected"));
    private static final CTSpriteShiftEntry BOTTOM = CTSpriteShifter.getCT(AllCTTypes.RECTANGLE,
            Voltworks.asResource("block/battery/battery_bottom"), Voltworks.asResource("block/battery/battery_bottom_connected"));

    static {
        for (int i = 0; i <= BatterySpec.GAUGE_LEVELS; i++)
            SIDE[i] = CTSpriteShifter.getCT(AllCTTypes.RECTANGLE,
                    Voltworks.asResource("block/battery/battery_side_" + i), Voltworks.asResource("block/battery/battery_side_" + i + "_connected"));
    }

    @Override
    public @Nullable CTSpriteShiftEntry getShift(BlockState state, Direction direction, @Nullable TextureAtlasSprite sprite) {
        if (direction.getAxis().isVertical()) {
            // wells and posts keep their own textures
            if (sprite == null)
                return direction == Direction.UP ? TOP : BOTTOM;
            String name = sprite.contents().name().getPath();
            return name.endsWith("battery_top") ? TOP : name.endsWith("battery_bottom") ? BOTTOM : null;
        }
        if (sprite != null && !sprite.contents().name().getPath().contains("battery_side_"))
            return null;
        return SIDE[state.getValue(BatteryBlock.CHARGE)];
    }

    @Override
    public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader, BlockPos pos, BlockPos otherPos, Direction face) {
        if (state.getBlock() != other.getBlock())
            return false;
        return reader.getBlockEntity(pos) instanceof BatteryBlockEntity a && reader.getBlockEntity(otherPos) instanceof BatteryBlockEntity b
                && a.controllerPos().equals(b.controllerPos());
    }
}
