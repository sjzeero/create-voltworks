package io.github.sjzeero.voltworks.servo;

import com.george_vi.electroenergetics.CEENodeConfigurations;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.ProperOilAndWaterloggedBlock;
import com.george_vi.electroenergetics.foundation.base.DirectionalKineticElectricBlock;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import com.simibubi.create.foundation.block.IBE;
import io.github.sjzeero.voltworks.Voltworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

import java.util.Map;

public class ServoBlock extends DirectionalKineticElectricBlock<ServoDevice>
        implements IBE<ServoBlockEntity>, ProperOilAndWaterloggedBlock {
    public static final BooleanProperty ROLL = DirectionalRolledDeviceBlock.ROLL;
    public static final EnumProperty<ProperOilAndWaterloggedBlock.LoggedState> LOGGED_STATE = ProperOilAndWaterloggedBlock.LOGGED_STATE;

    public ServoBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(LOGGED_STATE, ProperOilAndWaterloggedBlock.LoggedState.DRY)
                .setValue(ROLL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LOGGED_STATE, ROLL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferred = getPreferredFacing(context);
        if (preferred == null || context.getPlayer().isShiftKeyDown()) {
            preferred = context.getNearestLookingDirection();
        }
        if (!context.getPlayer().isShiftKeyDown()) {
            preferred = preferred.getOpposite();
        }
        if (preferred.getAxis().isVertical()) {
            return withWater(defaultBlockState()
                    .setValue(FACING, preferred)
                    .setValue(ROLL, context.getHorizontalDirection().getAxis() == Direction.Axis.X), context);
        }
        return withWater(defaultBlockState().setValue(FACING, preferred), context);
    }

    @Override
    public SimulatedDeviceType<ServoDevice> getDevice() {
        return Voltworks.SERVO_DEVICE.get();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        updateWater(level, state, pos);
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!player.mayBuild() || player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.isEmpty()) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            withBlockEntityDo(level, pos, be -> {
                if (be.running) {
                    be.disassemble();
                } else {
                    be.assembleNextTick = true;
                }
            });
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        InteractionResult result = super.onWrenched(state, context);
        if (!context.getLevel().isClientSide && result.consumesAction()) {
            BlockEntity be = context.getLevel().getBlockEntity(context.getClickedPos());
            if (be instanceof ServoBlockEntity sbe) {
                sbe.disassemble();
            }
        }
        return result;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return fluidState(state);
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
        return CEENodeConfigurations.ELECTRIC_MOTOR.getNodes(state.getValue(FACING), state.getValue(ROLL));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return CEENodeConfigurations.ELECTRIC_MOTOR.getNodePos(state.getValue(FACING), state.getValue(ROLL), id);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        if (targetedFace.getAxis() == originalState.getValue(FACING).getAxis()) {
            return originalState.cycle(ROLL);
        }
        return super.getRotatedBlockState(originalState, targetedFace);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        if (state.getValue(FACING).getAxis() == Direction.Axis.Y && (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)) {
            return state.cycle(ROLL);
        }
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public Class<ServoBlockEntity> getBlockEntityClass() {
        return ServoBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ServoBlockEntity> getBlockEntityType() {
        return Voltworks.SERVO_BE.get();
    }
}
