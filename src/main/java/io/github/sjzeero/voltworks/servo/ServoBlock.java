/*
 * Copyright 2025-2026 patryk3211
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
 * Modified by sjzeero: ported from Create: Power Grid to Voltworks.
 */
package io.github.sjzeero.voltworks.servo;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.DirectionalKineticElectricBlock;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import io.github.sjzeero.voltworks.Voltworks;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import io.github.sjzeero.voltworks.registry.VoltworksBlockEntities;
import io.github.sjzeero.voltworks.registry.VoltworksDevices;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class ServoBlock extends DirectionalKineticElectricBlock<ServoDevice> implements IBE<ServoBlockEntity> {
    public static final BooleanProperty ROLL = DirectionalRolledDeviceBlock.ROLL;
    private static final Map<String, Vec3[]> NODES = new HashMap<>();
    static {
        NODES.put("NORTHfalse", new Vec3[]{new Vec3(0.3125, 0.8125, 0.9375), new Vec3(0.6875, 0.8125, 0.9375), new Vec3(0.5, 0.8125, 0.9375)});
        NODES.put("NORTHtrue", new Vec3[]{new Vec3(0.3125, 0.8125, 0.9375), new Vec3(0.6875, 0.8125, 0.9375), new Vec3(0.5, 0.8125, 0.9375)});
        NODES.put("EASTfalse", new Vec3[]{new Vec3(0.0625, 0.8125, 0.3125), new Vec3(0.0625, 0.8125, 0.6875), new Vec3(0.0625, 0.8125, 0.5)});
        NODES.put("EASTtrue", new Vec3[]{new Vec3(0.0625, 0.8125, 0.3125), new Vec3(0.0625, 0.8125, 0.6875), new Vec3(0.0625, 0.8125, 0.5)});
        NODES.put("SOUTHfalse", new Vec3[]{new Vec3(0.6875, 0.8125, 0.0625), new Vec3(0.3125, 0.8125, 0.0625), new Vec3(0.5, 0.8125, 0.0625)});
        NODES.put("SOUTHtrue", new Vec3[]{new Vec3(0.6875, 0.8125, 0.0625), new Vec3(0.3125, 0.8125, 0.0625), new Vec3(0.5, 0.8125, 0.0625)});
        NODES.put("WESTfalse", new Vec3[]{new Vec3(0.9375, 0.8125, 0.6875), new Vec3(0.9375, 0.8125, 0.3125), new Vec3(0.9375, 0.8125, 0.5)});
        NODES.put("WESTtrue", new Vec3[]{new Vec3(0.9375, 0.8125, 0.6875), new Vec3(0.9375, 0.8125, 0.3125), new Vec3(0.9375, 0.8125, 0.5)});
        NODES.put("UPfalse", new Vec3[]{new Vec3(0.3125, 0.0625, 0.8125), new Vec3(0.6875, 0.0625, 0.8125), new Vec3(0.5, 0.0625, 0.8125)});
        NODES.put("UPtrue", new Vec3[]{new Vec3(0.1875, 0.0625, 0.3125), new Vec3(0.1875, 0.0625, 0.6875), new Vec3(0.1875, 0.0625, 0.5)});
        NODES.put("DOWNfalse", new Vec3[]{new Vec3(0.3125, 0.9375, 0.1875), new Vec3(0.6875, 0.9375, 0.1875), new Vec3(0.5, 0.9375, 0.1875)});
        NODES.put("DOWNtrue", new Vec3[]{new Vec3(0.8125, 0.9375, 0.3125), new Vec3(0.8125, 0.9375, 0.6875), new Vec3(0.8125, 0.9375, 0.5)});
    }

    private static Vec3[] nodes(BlockState state) {
        Vec3[] n = NODES.get(state.getValue(FACING).name() + state.getValue(ROLL));
        return n != null ? n : NODES.get("NORTHfalse");
    }
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
    static {
        for (Direction d : Direction.values()) SHAPES.put(d, shapeFor(d));
    }

    private static VoxelShape shapeFor(Direction f) {
        double[][] boxes = {{3, 3, 0.5, 13, 13, 15.5}, {2.5, 2.5, 0.5, 13.5, 13.5, 3.5}};
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
            s = Shapes.or(s, Block.box(r[0], r[1], r[2], r[3], r[4], r[5]));
        }
        return s;
    }

    public ServoBlock(Properties properties) {
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
    public SimulatedDeviceType<ServoDevice> getDevice() {
        return VoltworksDevices.SERVO_DEVICE.get();
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
        Map<Integer, Vec3> m = new HashMap<>();
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
    public Class<ServoBlockEntity> getBlockEntityClass() {
        return ServoBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ServoBlockEntity> getBlockEntityType() {
        return VoltworksBlockEntities.SERVO_BE.get();
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return Component.translatable("voltworks.node.servo_" + id);
    }
}
