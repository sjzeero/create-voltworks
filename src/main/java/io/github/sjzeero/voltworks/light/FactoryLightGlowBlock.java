package io.github.sjzeero.voltworks.light;

import io.github.sjzeero.voltworks.VoltworksConfig;
import io.github.sjzeero.voltworks.registry.VoltworksBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FactoryLightGlowBlock extends Block {
    public FactoryLightGlowBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FactoryLightBlock.POWER, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FactoryLightBlock.POWER);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!lit(level, pos))
            level.removeBlock(pos, false);
    }

    public static void project(Level level, BlockPos lamp, int power) {
        int range = VoltworksConfig.factoryLightRange();
        BlockPos.MutableBlockPos p = lamp.mutable();
        for (int i = 0; i < range; i++) {
            p.move(Direction.DOWN);
            if (level.isOutsideBuildHeight(p))
                return;
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof FactoryLightGlowBlock) {
                if (power <= 0)
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                else if (s.getValue(FactoryLightBlock.POWER) != power)
                    level.setBlock(p, s.setValue(FactoryLightBlock.POWER, power), Block.UPDATE_ALL);
            } else if (power > 0 && s.isAir()) {
                level.setBlock(p, VoltworksBlocks.FACTORY_LIGHT_GLOW.get().defaultBlockState().setValue(FactoryLightBlock.POWER, power), Block.UPDATE_ALL);
            } else {
                if (!(s.getBlock() instanceof FactoryLightBlock))
                    clearBelow(level, p, range - i - 1);
                return;
            }
        }
    }

    private static void clearBelow(Level level, BlockPos from, int count) {
        BlockPos.MutableBlockPos p = from.mutable();
        for (int i = 0; i < count; i++) {
            p.move(Direction.DOWN);
            if (level.isOutsideBuildHeight(p) || !(level.getBlockState(p).getBlock() instanceof FactoryLightGlowBlock))
                return;
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static boolean lit(Level level, BlockPos pos) {
        int range = VoltworksConfig.factoryLightRange();
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 0; i < range; i++) {
            p.move(Direction.UP);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof FactoryLightBlock)
                return s.getValue(FactoryLightBlock.POWER) > 0;
            if (!(s.getBlock() instanceof FactoryLightGlowBlock))
                return false;
        }
        return false;
    }
}
