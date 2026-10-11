package io.github.sjzeero.voltworks.solar;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import io.github.sjzeero.voltworks.Voltworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;
import io.github.sjzeero.voltworks.registry.VoltworksDevices;

/**
 * Ported from Create: Power Grid (patryk3211) to Voltworks by sjzeero; electrical side reimplemented on CEE devices.
 * A 4 px thick panel; FACING points at its back, the cells face the opposite way.
 */
public class SolarPanelBlock extends SimpleElectricalDeviceBlock<SolarPanelDevice> {
    private static final int PLACEMENT_HELPER_ID = net.createmod.catnip.placement.PlacementHelpers.register(new PanelPlacementHelper());
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape Y = Block.box(0, 6, 0, 16, 10, 16), X = Block.box(6, 0, 0, 10, 16, 16), Z = Block.box(0, 0, 6, 16, 16, 10);

    public SolarPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING).getAxis()) {
            case X -> X;
            case Y -> Y;
            case Z -> Z;
        };
    }

    @Override
    public SimulatedDeviceType<SolarPanelDevice> getDevice() {
        return VoltworksDevices.SOLAR_PANEL_DEVICE.get();
    }

    /** Two terminals on the back face, 4 px either side of the centre: 0 = +, 1 = -. */
    private static Vec3 node(BlockState state, int id) {
        Direction f = state.getValue(FACING);
        Direction side = f.getAxis().isVertical() ? Direction.EAST : f.getClockWise();
        var b = f.getNormal();
        var s = side.getNormal();
        double k = id == 0 ? 4 / 16.0 : -4 / 16.0;
        return new Vec3(0.5 + b.getX() * 3 / 16.0 + s.getX() * k, 0.5 + b.getY() * 3 / 16.0 + s.getY() * k, 0.5 + b.getZ() * 3 / 16.0 + s.getZ() * k);
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        Map<Integer, Vec3> m = new HashMap<>();
        m.put(0, node(state, 0));
        m.put(1, node(state, 1));
        return m;
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return node(state, id);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public net.minecraft.network.chat.MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return net.minecraft.network.chat.Component.translatable("voltworks.node.solar_panel_" + id);
    }

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        return other.getBlock() instanceof SolarPanelBlock && other.getValue(FACING) == state.getValue(FACING);
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        var helper = net.createmod.catnip.placement.PlacementHelpers.get(PLACEMENT_HELPER_ID);
        if (!player.isShiftKeyDown() && player.mayBuild() && helper.matchesItem(stack)) {
            var offset = helper.getOffset(player, level, state, pos, hit);
            if (offset.isSuccessful()) {
                offset.placeInWorld(level, (net.minecraft.world.item.BlockItem) stack.getItem(), player, hand, hit);
                return net.minecraft.world.ItemInteractionResult.SUCCESS;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** Ported from Create: Power Grid (patryk3211, Apache-2.0): ghost preview + side placement keeping the panel facing. */
    private static class PanelPlacementHelper implements net.createmod.catnip.placement.IPlacementHelper {
        @Override
        public java.util.function.Predicate<net.minecraft.world.item.ItemStack> getItemPredicate() {
            return i -> i.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof SolarPanelBlock;
        }

        @Override
        public java.util.function.Predicate<BlockState> getStatePredicate() {
            return s -> s.getBlock() instanceof SolarPanelBlock;
        }

        @Override
        public net.createmod.catnip.placement.PlacementOffset getOffset(net.minecraft.world.entity.player.Player player, Level world, BlockState state, BlockPos pos,
                net.minecraft.world.phys.BlockHitResult ray) {
            var dirs = net.createmod.catnip.placement.IPlacementHelper.orderedByDistanceExceptAxis(pos, ray.getLocation(),
                    state.getValue(FACING).getAxis(), d -> world.getBlockState(pos.relative(d)).canBeReplaced());
            if (dirs.isEmpty()) return net.createmod.catnip.placement.PlacementOffset.fail();
            return net.createmod.catnip.placement.PlacementOffset.success(pos.relative(dirs.get(0)), s -> s.setValue(FACING, state.getValue(FACING)));
        }
    }
}
