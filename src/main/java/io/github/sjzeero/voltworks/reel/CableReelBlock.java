package io.github.sjzeero.voltworks.reel;

import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.simibubi.create.foundation.block.IBE;
import io.github.sjzeero.voltworks.registry.VoltworksBlockEntities;
import io.github.sjzeero.voltworks.registry.VoltworksDevices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

public class CableReelBlock extends SimpleElectricalDeviceBlock<CableReelDevice> implements IBE<CableReelBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, ReelMath.FILL_LEVELS);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape SMALL_SHAPE = Block.box(1, 0, 1, 15, 11, 15);

    private static final double[][] CORD_NODES = {{5, 16, 15}, {11, 16, 15}, ReelOutlet.PX, ReelOutlet.PX};
    private static final double[][] CABLE_NODES = {{8, 16, 15}, ReelOutlet.PX};
    private static final double[][] SMALL_CORD_NODES = {{3, 13, 12}, {13, 13, 12}, ReelOutlet.PX_SMALL, ReelOutlet.PX_SMALL};
    private static final double[][] SMALL_CABLE_NODES = {{13, 13, 12}, ReelOutlet.PX_SMALL};

    public final boolean cord;
    public final boolean small;

    public CableReelBlock(Properties properties, boolean cord) {
        this(properties, cord, false);
    }

    public CableReelBlock(Properties properties, boolean cord, boolean small) {
        super(properties);
        this.cord = cord;
        this.small = small;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(FILL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FILL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return small ? SMALL_SHAPE : SHAPE;
    }

    public int outletId() {
        return cord ? 2 : 1;
    }

    public boolean isOutlet(int id) {
        return cord ? id == 2 || id == 3 : id == 1;
    }

    public int nodeCount() {
        return cord ? 4 : 2;
    }

    static Vec3 rotated(Direction facing, double px, double py, double pz) {
        org.joml.Vector3d v = ReelOutlet.rotated(quarter(facing), px, py, pz);
        return new Vec3(v.x, v.y, v.z);
    }

    static int quarter(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    public Vec3 outletDir(BlockState state) {
        org.joml.Vector3d d = ReelOutlet.dir(quarter(state.getValue(FACING)));
        return new Vec3(d.x, d.y, d.z);
    }

    public Vec3 localNode(BlockState state, int id) {
        double[][] nodes = small ? (cord ? SMALL_CORD_NODES : SMALL_CABLE_NODES) : (cord ? CORD_NODES : CABLE_NODES);
        double[] n = nodes[id >= 0 && id < nodes.length ? id : 0];
        return rotated(state.getValue(FACING), n[0], n[1], n[2]);
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        Map<Integer, Vec3> m = new HashMap<>();
        for (int i = 0; i < nodeCount(); i++)
            m.put(i, localNode(state, i));
        return m;
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return localNode(state, id);
    }

    @Override
    public boolean isNodeAccessible(Level level, BlockPos pos, BlockState state, int id) {
        return !isOutlet(id);
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        if (isOutlet(id))
            return Component.translatable("voltworks.node.cable_reel_outlet");
        return Component.translatable(cord ? "voltworks.node.socket_" + id : "voltworks.node.cable_reel_terminal");
    }

    public boolean nearTerminal(BlockState state, BlockPos pos, Vec3 hit) {
        Vec3 local = hit.subtract(pos.getX(), pos.getY(), pos.getZ());
        for (int i = 0; i < nodeCount(); i++)
            if (!isOutlet(i) && localNode(state, i).distanceTo(local) < 0.3)
                return true;
        return false;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof ReelPlugItem)
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (!(stack.getItem() instanceof WireSpoolItem) || nearTerminal(state, pos, hit.getLocation()))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide)
            return ItemInteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof CableReelBlockEntity be && player instanceof ServerPlayer sp)
            be.load(sp, stack);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof CableReelBlockEntity be && player instanceof ServerPlayer sp) {
            if (player.isShiftKeyDown())
                be.retractByPlayer(sp);
            else
                be.grab(sp);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl && level.getBlockEntity(pos) instanceof CableReelBlockEntity be)
            be.windInAndDrop(sl);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public SimulatedDeviceType<CableReelDevice> getDevice() {
        return cord ? VoltworksDevices.CORD_REEL_DEVICE.get() : VoltworksDevices.CABLE_REEL_DEVICE.get();
    }

    @Override
    public Class<CableReelBlockEntity> getBlockEntityClass() {
        return CableReelBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CableReelBlockEntity> getBlockEntityType() {
        return VoltworksBlockEntities.CABLE_REEL_BE.get();
    }
}
