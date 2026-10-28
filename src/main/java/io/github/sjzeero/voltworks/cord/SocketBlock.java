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
package io.github.sjzeero.voltworks.cord;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import io.github.sjzeero.voltworks.registry.VoltworksDevices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public class SocketBlock extends SimpleElectricalDeviceBlock<SocketDevice> {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 3);
    public static final EnumProperty<Plug> PLUG = EnumProperty.create("plug", Plug.class);
    public static final int POSITIVE = 0, NEGATIVE = 1, CORD_POSITIVE = 2, CORD_NEGATIVE = 3;

    public enum Plug implements StringRepresentable {
        NONE, A, B;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final double[] PLUG_BACK = {12, 4, 8};

    private static final double[][] FLOOR_NODES = {{8, 1, 3.5}, {8, 1, 12.5}, {8, 3.5, 8}, {8, 3.5, 8}};
    private static final double[] FLOOR_BOX = {4, 0, 4, 12, 3, 12};
    private static final int[] FLOOR_Y = {-90, 0, 90, 180};
    private static final int[] WALL_X = {-90, 180, 90, 0};

    private static final Map<BlockState, Vec3[]> NODE_CACHE = new HashMap<>();
    private static final Map<BlockState, VoxelShape> SHAPE_CACHE = new HashMap<>();

    public SocketBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.DOWN).setValue(ROTATION, 0).setValue(PLUG, Plug.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ROTATION, PLUG);
    }

    private static Vec3 place(BlockState state, double px, double py, double pz) {
        Direction facing = state.getValue(FACING);
        int rot = state.getValue(ROTATION);
        boolean wall = facing.getAxis().isHorizontal();
        double x = px, y = py, z = pz;
        if (wall) {
            x = 16 - py;
            y = px;
        }
        Vector3f v = new Vector3f((float) (x / 16 - 0.5), (float) (y / 16 - 0.5), (float) (z / 16 - 0.5));
        blockstateRotation(state).transform(v);
        return new Vec3(round(v.x + 0.5), round(v.y + 0.5), round(v.z + 0.5));
    }

    private static Quaternionf blockstateRotation(BlockState state) {
        Direction facing = state.getValue(FACING);
        int rot = state.getValue(ROTATION);
        boolean wall = facing.getAxis().isHorizontal();
        int rx = wall ? WALL_X[rot] : facing == Direction.UP ? 180 : 0;
        int ry = !wall ? FLOOR_Y[rot] : switch (facing) {
            case NORTH -> -90;
            case SOUTH -> 90;
            case WEST -> 180;
            default -> 0;
        };
        return new Quaternionf().rotateYXZ((float) Math.toRadians(-ry), (float) Math.toRadians(-rx), 0);
    }

    public static Quaternionf modelRotation(BlockState state) {
        Quaternionf q = blockstateRotation(state);
        if (state.getValue(FACING).getAxis().isHorizontal())
            q.rotateZ((float) (Math.PI / 2));
        if (state.getValue(PLUG) == Plug.B)
            q.rotateY((float) Math.PI);
        return q;
    }

    public static Vector3f plugAxis(BlockState state) {
        return modelRotation(state.setValue(PLUG, Plug.A)).transform(new Vector3f(1, 0, 0));
    }

    private static double round(double v) {
        return Math.round(v * 1024) / 1024.0;
    }

    private static Vec3[] nodes(BlockState state) {
        return NODE_CACHE.computeIfAbsent(state, s -> {
            Vec3[] out = new Vec3[FLOOR_NODES.length];
            for (int i = 0; i < out.length; i++)
                out[i] = place(s, FLOOR_NODES[i][0], FLOOR_NODES[i][1], FLOOR_NODES[i][2]);
            Plug plug = s.getValue(PLUG);
            if (plug != Plug.NONE) {
                double bx = plug == Plug.A ? PLUG_BACK[0] : 16 - PLUG_BACK[0];
                out[CORD_POSITIVE] = out[CORD_NEGATIVE] = place(s, bx, PLUG_BACK[1], PLUG_BACK[2]);
            }
            return out;
        });
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE_CACHE.computeIfAbsent(state, s -> {
            Vec3 a = place(s, FLOOR_BOX[0], FLOOR_BOX[1], FLOOR_BOX[2]);
            Vec3 b = place(s, FLOOR_BOX[3], FLOOR_BOX[4], FLOOR_BOX[5]);
            return Block.box(Math.min(a.x, b.x) * 16, Math.min(a.y, b.y) * 16, Math.min(a.z, b.z) * 16,
                    Math.max(a.x, b.x) * 16, Math.max(a.y, b.y) * 16, Math.max(a.z, b.z) * 16);
        });
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace().getOpposite();
        int rotation = facing.getAxis().isVertical() ? context.getHorizontalDirection().get2DDataValue() : 3;
        return defaultBlockState().setValue(FACING, facing).setValue(ROTATION, rotation);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState.cycle(ROTATION);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        boolean cutters = LooseCords.isCutters(player.getMainHandItem());
        if (state.getValue(PLUG) == Plug.NONE || !cutters && !canUnplugWith(player))
            return InteractionResult.PASS;
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp))
            return InteractionResult.SUCCESS;
        LooseCords cords = LooseCords.get(sl);
        if (cutters ? cords.cutAt(sl, pos, CORD_POSITIVE, sp) : cords.useOutlet(sl, pos, CORD_POSITIVE, sp))
            return InteractionResult.SUCCESS;
        sl.setBlock(pos, state.setValue(PLUG, Plug.NONE), Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel sl)
            LooseCords.get(sl).blockBroken(sl, pos);
        return super.playerWillDestroy(level, pos, state, player);
    }

    private static boolean canUnplugWith(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty())
            return true;
        if (player.isShiftKeyDown() || held.getItem() instanceof BlockItem)
            return false;
        String ns = BuiltInRegistries.ITEM.getKey(held.getItem()).getNamespace();
        return !ns.equals("electroenergetics") && !ns.equals("create") && !ns.equals("voltworks");
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        Vec3[] n = nodes(state);
        Map<Integer, Vec3> m = new HashMap<>();
        for (int i = 0; i < n.length; i++)
            m.put(i, n[i]);
        return m;
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        Vec3[] n = nodes(state);
        return id >= 0 && id < n.length ? n[id] : n[0];
    }

    @Override
    public boolean isNodeAccessible(Level level, BlockPos pos, BlockState state, int id) {
        return id == POSITIVE || id == NEGATIVE;
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return Component.translatable("voltworks.node.socket_" + Math.min(id, 2));
    }

    @Override
    public SimulatedDeviceType<SocketDevice> getDevice() {
        return VoltworksDevices.SOCKET_DEVICE.get();
    }
}
