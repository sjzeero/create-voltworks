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
package io.github.sjzeero.voltworks.light;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import io.github.sjzeero.voltworks.registry.VoltworksDevices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

public class FactoryLightBlock extends SimpleElectricalDeviceBlock<FactoryLightDevice> {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 3);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 6);
    public static final BooleanProperty FLIP = BooleanProperty.create("flip");

    public FactoryLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X).setValue(POWER, 0).setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AXIS, POWER, PART, FLIP);
    }

    private static boolean joins(BlockGetter level, BlockPos pos, Direction.Axis axis) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof FactoryLightBlock && s.getValue(AXIS) == axis;
    }

    private static int partAt(BlockGetter level, BlockPos pos, Direction.Axis axis) {
        Direction neg = axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH;
        boolean n = joins(level, pos.relative(neg), axis), p = joins(level, pos.relative(neg.getOpposite()), axis);
        boolean z = axis == Direction.Axis.Z;
        if (n && p) return z ? 2 : 5;
        if (p) return z ? 1 : 4;
        if (n) return z ? 3 : 6;
        return 0;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        Direction face = context.getClickedFace();
        BlockState behind = level.getBlockState(context.getClickedPos().relative(face, -1));
        Direction.Axis axis = behind.getBlock() instanceof FactoryLightBlock && face.getAxis().isHorizontal() && behind.getValue(AXIS) == face.getAxis()
                ? face.getAxis() : context.getHorizontalDirection().getAxis();
        return defaultBlockState().setValue(AXIS, axis).setValue(PART, partAt(level, context.getClickedPos(), axis));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState s = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        if (s.getBlock() instanceof FactoryLightBlock && direction.getAxis().isHorizontal())
            return s.setValue(PART, partAt(level, pos, s.getValue(AXIS)));
        return s;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int part = state.getValue(PART);
        boolean neg = part == 1 || part == 4, posEnd = part == 3 || part == 6;
        int min = neg || part == 0 ? 1 : 0, max = posEnd || part == 0 ? 15 : 16;
        return state.getValue(AXIS) == Direction.Axis.Z ? Block.box(1, 8, min, 15, 16, max) : Block.box(min, 8, 1, max, 16, 15);
    }

    @Override
    public SimulatedDeviceType<FactoryLightDevice> getDevice() {
        return VoltworksDevices.FACTORY_LIGHT_DEVICE.get();
    }

    private static Vec3 node(BlockState state, int id) {
        double k = ((id == 0) != state.getValue(FLIP)) ? 6 / 16.0 : -6 / 16.0;
        return state.getValue(AXIS) == Direction.Axis.X ? new Vec3(0.5 + k, 9 / 16.0, 0.5) : new Vec3(0.5, 9 / 16.0, 0.5 + k);
    }

    private static boolean hasNode(BlockState state, int id) {
        int part = state.getValue(PART);
        if (part == 0) return true;
        boolean negEnd = part == 1 || part == 4;
        boolean posEnd = part == 3 || part == 6;
        return ((id == 0) != state.getValue(FLIP)) ? posEnd : negEnd;
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        Map<Integer, Vec3> m = new HashMap<>();
        for (int id = 0; id < 2; id++) if (hasNode(state, id)) m.put(id, node(state, id));
        return m;
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return node(state, id);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.NONE) return state;
        int part = state.getValue(PART);
        boolean z = state.getValue(AXIS) == Direction.Axis.Z;
        boolean turn = rotation != Rotation.CLOCKWISE_180;
        boolean cw = rotation == Rotation.CLOCKWISE_90;
        int out = part;
        if (part != 0) {
            if (!turn) out = switch (part) { case 1 -> 3; case 3 -> 1; case 4 -> 6; case 6 -> 4; default -> part; };
            else if (z) out = switch (part) { case 1 -> cw ? 6 : 4; case 3 -> cw ? 4 : 6; default -> 5; };
            else out = switch (part) { case 4 -> cw ? 1 : 3; case 6 -> cw ? 3 : 1; default -> 2; };
        }
        Direction d0 = Direction.get(state.getValue(FLIP) ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE, state.getValue(AXIS));
        Direction nd = rotation.rotate(d0);
        return state.setValue(AXIS, nd.getAxis()).setValue(FLIP, nd.getAxisDirection() == Direction.AxisDirection.NEGATIVE).setValue(PART, out);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return Component.translatable("voltworks.node.factory_light_" + id);
    }
}
