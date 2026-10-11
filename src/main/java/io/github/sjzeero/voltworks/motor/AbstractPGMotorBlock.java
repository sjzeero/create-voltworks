package io.github.sjzeero.voltworks.motor;

import com.george_vi.electroenergetics.CEENodeConfigurations;
import com.george_vi.electroenergetics.foundation.base.DirectionalKineticElectricBlock;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ported from Create: Power Grid (patryk3211) to Voltworks by sjzeero; electrical side reimplemented on CEE devices.
 */
public abstract class AbstractPGMotorBlock extends DirectionalKineticElectricBlock<PGMotorDevice> {
    public static final BooleanProperty ROLL = DirectionalRolledDeviceBlock.ROLL;
    /** Node positions (0:+ red, 1:- blue) taken from the PG motor model pins and the blockstate rotations. */
    private static final Map<String, Vec3[]> NODES = new java.util.HashMap<>();
    static {
        NODES.put("NORTHfalse", new Vec3[]{new Vec3(0.375, 0.84375, 0.9375), new Vec3(0.625, 0.84375, 0.9375)});
        NODES.put("NORTHtrue", new Vec3[]{new Vec3(0.375, 0.84375, 0.9375), new Vec3(0.625, 0.84375, 0.9375)});
        NODES.put("EASTfalse", new Vec3[]{new Vec3(0.0625, 0.84375, 0.375), new Vec3(0.0625, 0.84375, 0.625)});
        NODES.put("EASTtrue", new Vec3[]{new Vec3(0.0625, 0.84375, 0.375), new Vec3(0.0625, 0.84375, 0.625)});
        NODES.put("SOUTHfalse", new Vec3[]{new Vec3(0.625, 0.84375, 0.0625), new Vec3(0.375, 0.84375, 0.0625)});
        NODES.put("SOUTHtrue", new Vec3[]{new Vec3(0.625, 0.84375, 0.0625), new Vec3(0.375, 0.84375, 0.0625)});
        NODES.put("WESTfalse", new Vec3[]{new Vec3(0.9375, 0.84375, 0.625), new Vec3(0.9375, 0.84375, 0.375)});
        NODES.put("WESTtrue", new Vec3[]{new Vec3(0.9375, 0.84375, 0.625), new Vec3(0.9375, 0.84375, 0.375)});
        NODES.put("UPfalse", new Vec3[]{new Vec3(0.375, 0.0625, 0.84375), new Vec3(0.625, 0.0625, 0.84375)});
        NODES.put("UPtrue", new Vec3[]{new Vec3(0.15625, 0.0625, 0.375), new Vec3(0.15625, 0.0625, 0.625)});
        NODES.put("DOWNfalse", new Vec3[]{new Vec3(0.375, 0.9375, 0.15625), new Vec3(0.625, 0.9375, 0.15625)});
        NODES.put("DOWNtrue", new Vec3[]{new Vec3(0.84375, 0.9375, 0.375), new Vec3(0.84375, 0.9375, 0.625)});
    }

    private static Vec3[] nodes(BlockState state) {
        Vec3[] n = NODES.get(state.getValue(FACING).name() + state.getValue(ROLL));
        return n != null ? n : NODES.get("NORTHfalse");
    }

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.values()) SHAPES.put(d, shapeFor(d));
    }

    private static VoxelShape box16(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Block.box(x1, y1, z1, x2, y2, z2);
    }

    /** PG motor shape (north: body 3..13 along z 0.5..15.5, flange at the back), rotated for every facing. */
    private static VoxelShape shapeFor(Direction f) {
        double[][] boxes = {{3, 3, 0.5, 13, 13, 15.5}, {2.5, 2.5, 10.5, 13.5, 13.5, 15.5}};
        VoxelShape s = Shapes.empty();
        for (double[] b : boxes) {
            double[] r = switch (f) {
                case NORTH -> b;
                case SOUTH -> new double[]{16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]};
                case EAST -> new double[]{16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]};
                case WEST -> new double[]{b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]};
                case UP -> new double[]{b[0], 16 - b[5], b[1], b[3], 16 - b[2], b[4]};
                case DOWN -> new double[]{b[0], b[2], b[1], b[3], b[5], b[4]};
            };
            s = Shapes.or(s, box16(r[0], r[1], r[2], r[3], r[4], r[5]));
        }
        return s;
    }

    protected AbstractPGMotorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ROLL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ROLL);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    public Direction getPreferredFacing(BlockPlaceContext context) {
        Direction preferred = null;
        for (Direction side : Iterate.directions) {
            BlockState bs = context.getLevel().getBlockState(context.getClickedPos().relative(side));
            if (bs.getBlock() instanceof IRotate rot && rot.hasShaftTowards(context.getLevel(), context.getClickedPos().relative(side), bs, side.getOpposite())) {
                if (preferred != null && preferred.getAxis() != side.getAxis()) return null;
                preferred = side;
            }
        }
        return preferred == null ? null : preferred.getOpposite();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferred = getPreferredFacing(context);
        boolean sneak = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
        Direction facing;
        if (preferred == null || sneak) {
            Direction look = context.getNearestLookingDirection();
            facing = sneak ? look : look.getOpposite();
        } else {
            facing = preferred.getOpposite();
        }
        return defaultBlockState().setValue(FACING, facing)
                .setValue(ROLL, facing.getAxis().isVertical() && context.getHorizontalDirection().getAxis() == Direction.Axis.X);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        Vec3[] n = nodes(state);
        Map<Integer, Vec3> m = new java.util.HashMap<>();
        for (int i = 0; i < n.length; i++) m.put(i, n[i]);
        return m;
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        Vec3[] n = nodes(state);
        return id >= 0 && id < n.length ? n[id] : n[0];
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        if (targetedFace.getAxis() == originalState.getValue(FACING).getAxis()) return originalState.cycle(ROLL);
        return super.getRotatedBlockState(originalState, targetedFace);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        if (state.getValue(FACING).getAxis() == Direction.Axis.Y && (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90))
            return state.cycle(ROLL);
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public net.minecraft.network.chat.MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return net.minecraft.network.chat.Component.translatable("voltworks.node.motor_" + id);
    }
}
