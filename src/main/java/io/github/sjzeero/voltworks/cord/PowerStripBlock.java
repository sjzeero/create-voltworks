package io.github.sjzeero.voltworks.cord;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import io.github.sjzeero.voltworks.registry.VoltworksDevices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public class PowerStripBlock extends SimpleElectricalDeviceBlock<PowerStripDevice> {
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final double CORD_LENGTH = 4.0;

    private static final Map<BlockState, VoxelShape> SHAPES = new HashMap<>();

    public PowerStripBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACE, AttachFace.FLOOR).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }

    public static int yRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    public static int xRot(AttachFace face) {
        return switch (face) {
            case WALL -> 90;
            case CEILING -> 180;
            default -> 0;
        };
    }

    public static Quaternionf rotation(BlockState state) {
        return new Quaternionf().rotateYXZ((float) Math.toRadians(-yRot(state.getValue(FACING))),
                (float) Math.toRadians(-xRot(state.getValue(FACE))), 0);
    }

    public static Vec3 place(BlockState state, double[] px) {
        Vector3f v = new Vector3f((float) (px[0] / 16 - 0.5), (float) (px[1] / 16 - 0.5), (float) (px[2] / 16 - 0.5));
        rotation(state).transform(v);
        return new Vec3(SocketBlock.round(v.x + 0.5), SocketBlock.round(v.y + 0.5), SocketBlock.round(v.z + 0.5));
    }

    public static Vec3 turn(BlockState state, double[] dir) {
        Vector3f v = rotation(state).transform(new Vector3f((float) dir[0], (float) dir[1], (float) dir[2]));
        return new Vec3(v.x, v.y, v.z);
    }

    public static Vec3 unplace(BlockState state, Vec3 local) {
        Vector3f v = new Vector3f((float) (local.x - 0.5), (float) (local.y - 0.5), (float) (local.z - 0.5));
        rotation(state).conjugate(new Quaternionf()).transform(v);
        return new Vec3((v.x + 0.5) * 16, (v.y + 0.5) * 16, (v.z + 0.5) * 16);
    }

    public static int outletAt(BlockState state, Vec3 local) {
        Vec3 px = unplace(state, local);
        int best = -1;
        double bestD = 4.5 * 4.5;
        for (int supply : StripGeometry.OUTLETS) {
            double[] s = StripGeometry.slot(supply);
            double dx = px.x - s[0], dz = px.z - s[2];
            double d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = supply;
            }
        }
        return best;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.computeIfAbsent(state, s -> {
            Vec3 a = place(s, new double[]{1, 0, 1}), b = place(s, new double[]{15, 3, 15});
            return Block.box(Math.min(a.x, b.x) * 16, Math.min(a.y, b.y) * 16, Math.min(a.z, b.z) * 16,
                    Math.max(a.x, b.x) * 16, Math.max(a.y, b.y) * 16, Math.max(a.z, b.z) * 16);
        });
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        BlockState s = defaultBlockState();
        if (clicked == Direction.UP)
            return s.setValue(FACE, AttachFace.FLOOR).setValue(FACING, context.getHorizontalDirection());
        if (clicked == Direction.DOWN)
            return s.setValue(FACE, AttachFace.CEILING).setValue(FACING, context.getHorizontalDirection());
        return s.setValue(FACE, AttachFace.WALL).setValue(FACING, clicked);
    }

    public static BlockPos support(BlockState state, BlockPos pos) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> pos.below();
            case CEILING -> pos.above();
            default -> pos.relative(state.getValue(FACING).getOpposite());
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel sl && placer instanceof ServerPlayer sp)
            LooseCords.get(sl).plugStripInto(sl, pos, support(state, pos), placer.getEyePosition(), sp);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        ItemStack held = player.getMainHandItem();
        boolean cutters = LooseCords.isCutters(held);
        if (!held.isEmpty() && !cutters)
            return InteractionResult.PASS;
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp))
            return InteractionResult.SUCCESS;
        int outlet = outletAt(state, hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)));
        LooseCords cords = LooseCords.get(sl);
        if (cutters)
            return cords.cutAt(sl, pos, outlet < 0 ? StripGeometry.INPUT : outlet, sp) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        if (outlet >= 0 && cords.useOutlet(sl, pos, outlet, sp))
            return InteractionResult.SUCCESS;
        cords.useStrip(sl, pos, sp, player.isShiftKeyDown());
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel sl)
            LooseCords.get(sl).blockBroken(sl, pos);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        Map<Integer, Vec3> m = new HashMap<>();
        for (int i = 0; i < 8; i++)
            m.put(i, place(state, StripGeometry.node(i)));
        return m;
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return place(state, StripGeometry.node(id >= 0 && id < 8 ? id : 0));
    }

    @Override
    public boolean isNodeAccessible(Level level, BlockPos pos, BlockState state, int id) {
        return false;
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return Component.translatable(id < 2 ? "voltworks.node.power_strip_cord" : "voltworks.node.socket_2");
    }

    @Override
    public SimulatedDeviceType<PowerStripDevice> getDevice() {
        return VoltworksDevices.POWER_STRIP_DEVICE.get();
    }
}
