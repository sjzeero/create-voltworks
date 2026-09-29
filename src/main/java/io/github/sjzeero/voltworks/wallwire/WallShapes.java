package io.github.sjzeero.voltworks.wallwire;

import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocks as 16x16x16 pixel grids, taken from the outline shape of blocks that have a collision shape.
 * A pixel is solid when its centre lies inside one of the shape's boxes.
 */
public final class WallShapes {
    private static final long[] EMPTY = new long[64];
    private static final long[] FULL = new long[64];
    private static final Map<BlockState, long[]> CACHE = new ConcurrentHashMap<>();
    private static final Map<Block, Boolean> PASSABLE = new ConcurrentHashMap<>();

    static {
        Arrays.fill(FULL, -1L);
    }

    private final BlockGetter level;
    private final Long2ObjectOpenHashMap<long[]> blocks = new Long2ObjectOpenHashMap<>();

    public WallShapes(BlockGetter level) {
        this.level = level;
    }

    /** Whether the pixel at world pixel coordinates (16 per block) is solid. */
    public boolean solid(int x, int y, int z) {
        long[] mask = blockMask(x, y, z);
        int i = (x & 15) << 8 | (y & 15) << 4 | (z & 15);
        return (mask[i >> 6] >>> (i & 63) & 1) != 0;
    }

    /** Pixels from here along {@code d} that stay inside the same block, if that block is empty; 0 otherwise. */
    public int emptyRun(int x, int y, int z, Direction d) {
        if(blockMask(x, y, z) != EMPTY)
            return 0;
        int c = d.getAxis().choose(x, y, z) & 15;
        return d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 16 - c : c + 1;
    }

    private long[] blockMask(int x, int y, int z) {
        long key = BlockPos.asLong(x >> 4, y >> 4, z >> 4);
        long[] mask = blocks.get(key);
        if(mask == null) {
            mask = mask(level, BlockPos.of(key));
            blocks.put(key, mask);
        }
        return mask;
    }

    public static long[] mask(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if(state.isAir() || passable(state))
            return EMPTY;
        if(state.getBlock().hasDynamicShape() || state.hasOffsetFunction())
            return compute(level, pos, state);
        long[] mask = CACHE.get(state);
        if(mask == null) {
            mask = compute(level, pos, state);
            CACHE.put(state, mask);
        }
        return mask;
    }

    /** Copycat blocks, Create's own and those from Copycats+, which wires run through. */
    public static boolean passable(BlockState state) {
        return PASSABLE.computeIfAbsent(state.getBlock(), b -> b instanceof CopycatBlock
                || BuiltInRegistries.BLOCK.getKey(b).getNamespace().equals("copycats"));
    }

    private static long[] compute(BlockGetter level, BlockPos pos, BlockState state) {
        if(state.getCollisionShape(level, pos).isEmpty())
            return EMPTY;
        if(state.isCollisionShapeFullBlock(level, pos))
            return FULL;
        VoxelShape shape = state.getShape(level, pos);
        if(shape.isEmpty())
            return EMPTY;
        long[] mask = new long[64];
        for(AABB box : shape.toAabbs()) {
            int x0 = px(box.minX), x1 = px(box.maxX), y0 = px(box.minY), y1 = px(box.maxY), z0 = px(box.minZ), z1 = px(box.maxZ);
            for(int x = x0; x < x1; x++)
                for(int y = y0; y < y1; y++)
                    for(int z = z0; z < z1; z++) {
                        int i = x << 8 | y << 4 | z;
                        mask[i >> 6] |= 1L << (i & 63);
                    }
        }
        return mask;
    }

    private static int px(double v) {
        return Math.clamp(Math.round(v * 16), 0, 16);
    }
}
