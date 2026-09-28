/*
 * Copyright 2025 patryk3211
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
 * Modified by sjzeero: ported from Create: Power Grid to Create: Voltworks.
 */
package io.github.sjzeero.eecircuits.circuits.editor;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.simibubi.create.AllItems;
import io.github.sjzeero.eecircuits.EECircuits;
import io.github.sjzeero.eecircuits.utility.Lang;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class CircuitDesignTableBlock extends SimpleElectricalDeviceBlock<CircuitDesignTableDevice> implements EntityBlock {
    public static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShaper SHAPER = VoxelShaper.forHorizontal(Shapes.or(
            box(3, 0, 3, 13, 12, 13),
            box(0, 11, 2, 16, 14, 14),
            box(4, 3, 12, 12, 11, 14)
    ), Direction.NORTH);

    private static final Vec3[] SOCKET = { new Vec3(7 / 16d, 7 / 16d, 15 / 16d), new Vec3(9 / 16d, 7 / 16d, 15 / 16d) };

    public CircuitDesignTableBlock(Properties settings) {
        super(settings.lightLevel(state -> state.getValue(POWERED) ? 7 : 0));
        registerDefaultState(defaultBlockState().setValue(HORIZONTAL_FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HORIZONTAL_FACING, POWERED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public SimulatedDeviceType<CircuitDesignTableDevice> getDevice() {
        return EECircuits.DESIGN_TABLE_DEVICE.get();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPER.get(state.getValue(HORIZONTAL_FACING));
    }

    private static Vec3 socket(BlockState state, int id) {
        var v = SOCKET[id].subtract(0.5, 0.5, 0.5);
        v = switch(state.getValue(HORIZONTAL_FACING)) {
            case SOUTH -> v.yRot((float) Math.PI);
            case EAST -> v.yRot((float) -Math.PI / 2);
            case WEST -> v.yRot((float) Math.PI / 2);
            default -> v;
        };
        return v.add(0.5, 0.5, 0.5);
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return Map.of(0, socket(state, 0), 1, socket(state, 1));
    }

    @Override
    public @Nullable Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return id == 0 || id == 1 ? socket(state, id) : null;
    }

    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int id) {
        return 2 / 16f;
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return Component.translatable("voltworks.terminal.connector");
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if(io.github.sjzeero.eecircuits.utility.WiringTools.isWiringTool(player.getItemInHand(InteractionHand.MAIN_HAND)))
            return InteractionResult.PASS;
        if(level.isClientSide)
            return InteractionResult.SUCCESS;
        if(level.getBlockEntity(pos) instanceof CircuitDesignTableBlockEntity be) {
            if(!player.isCreative() && !be.isPowered()) {
                player.displayClientMessage(Lang.translate("message.circuit_table.no_power")
                        .style(ChatFormatting.RED)
                        .component(), true);
                return InteractionResult.SUCCESS;
            }
            IMultiScreenHandlerFactory.openScreen((ServerPlayer) player, be, be::sendToMenu, 0);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        double R = CircuitDesignTableDevice.RESISTANCE;
        float P = CircuitDesignTableDevice.REQUIRED_POWER;
        tooltip.add(Component.translatable("voltworks.tooltip.rated_voltage", Math.round(Math.sqrt(P * R))).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("voltworks.tooltip.rated_power", Math.round(P)).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CircuitDesignTableBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if(level.isClientSide || type != EECircuits.DESIGN_TABLE_BE.get())
            return null;
        return (l, p, s, be) -> CircuitDesignTableBlockEntity.serverTick(l, p, s, (CircuitDesignTableBlockEntity) be);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(HORIZONTAL_FACING, rot.rotate(state.getValue(HORIZONTAL_FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(HORIZONTAL_FACING)));
    }
}
