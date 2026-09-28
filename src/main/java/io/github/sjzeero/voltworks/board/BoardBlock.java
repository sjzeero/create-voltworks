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
 * Modified by sjzeero: ported from Create: Power Grid to CEE: Voltworks.
 */
package io.github.sjzeero.voltworks.board;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.components.properties.Orientation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import io.github.sjzeero.voltworks.circuits.editor.CircuitBoardEditMenu;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class BoardBlock extends SimpleElectricalDeviceBlock<BoardDevice> implements EntityBlock {
    public static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 2);
    public static final IntegerProperty LIGHT = BlockStateProperties.LEVEL;
    public static final EnumProperty<BoardColor> COLOR = EnumProperty.create("color", BoardColor.class);
    private static final AABB PLATE = new AABB(0, 0, 0, 1, 2 / 16d, 1);

    public BoardBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(LIGHT)));
        registerDefaultState(defaultBlockState().setValue(HORIZONTAL_FACING, Direction.NORTH).setValue(ROTATION, 0).setValue(LIGHT, 0).setValue(COLOR, BoardColor.GREEN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HORIZONTAL_FACING, ROTATION, LIGHT, COLOR);
    }

    @Override
    public SimulatedDeviceType<BoardDevice> getDevice() {
        return Voltworks.BOARD_DEVICE.get();
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var face = ctx.getClickedFace();
        if(face.getAxis() == Direction.Axis.Y) {
            var facing = ctx.getPlayer() == null || !ctx.getPlayer().isShiftKeyDown() ?
                    ctx.getHorizontalDirection() : ctx.getHorizontalDirection().getOpposite();
            return defaultBlockState()
                    .setValue(HORIZONTAL_FACING, facing)
                    .setValue(ROTATION, face == Direction.UP ? 0 : 2);
        }
        return defaultBlockState()
                .setValue(ROTATION, 1)
                .setValue(HORIZONTAL_FACING, face.getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if(data.contains("Schematic") && level.getBlockEntity(pos) instanceof BoardBlockEntity be)
            be.setSchematic(data.getCompound("Schematic"));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        var stack = super.getCloneItemStack(level, pos, state);
        if(level.getBlockEntity(pos) instanceof BoardBlockEntity be && !be.getSchematic().components().isEmpty()) {
            var tag = new CompoundTag();
            tag.put("Schematic", be.getSchematic().serializeSafeNbt(level.registryAccess()));
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return BoardColor.paint(stack, state.getValue(COLOR));
    }

    public static int getAngleX(BlockState state) {
        if(!(state.getBlock() instanceof BoardBlock))
            return 0;
        return state.getValue(ROTATION) * 90;
    }

    public static int getAngleY(BlockState state) {
        if(!(state.getBlock() instanceof BoardBlock))
            return 0;
        int angle = switch(state.getValue(HORIZONTAL_FACING)) {
            case EAST -> -90;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        };
        if(state.getValue(ROTATION) == 2)
            angle -= 180;
        return angle;
    }

    public static Vec3 rotate(Vec3 local, int angleX, int angleY) {
        var v = local.subtract(0.5, 0.5, 0.5);
        v = v.xRot((float) Math.toRadians(-angleX));
        v = v.yRot((float) Math.toRadians(angleY));
        return v.add(0.5, 0.5, 0.5);
    }

    public static Vec3 unrotate(Vec3 local, int angleX, int angleY) {
        var v = local.subtract(0.5, 0.5, 0.5);
        v = v.yRot((float) Math.toRadians(-angleY));
        v = v.xRot((float) Math.toRadians(angleX));
        return v.add(0.5, 0.5, 0.5);
    }

    public static Vec3 toLocal(BlockState state, Vec3 local) {
        return rotate(local, getAngleX(state), getAngleY(state));
    }

    public static Vec3 toWorld(BlockState state, BlockPos pos, Vec3 local) {
        return toLocal(state, local).add(pos.getX(), pos.getY(), pos.getZ());
    }

    private static AABB rotate(AABB box, BlockState state) {
        var a = toLocal(state, new Vec3(box.minX, box.minY, box.minZ));
        var b = toLocal(state, new Vec3(box.maxX, box.maxY, box.maxZ));
        return new AABB(a, b);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var shape = plateShape(state);
        if(level.getBlockEntity(pos) instanceof BoardBlockEntity be) {
            for(var interactable : be.interactables())
                shape = Shapes.or(shape, Shapes.create(rotate(interactable.box(), state)));
        }
        return shape;
    }

    private static final Map<BlockState, VoxelShape> PLATE_SHAPES = new java.util.concurrent.ConcurrentHashMap<>();

    private static VoxelShape plateShape(BlockState state) {
        return PLATE_SHAPES.computeIfAbsent(state.setValue(LIGHT, 0).setValue(COLOR, BoardColor.GREEN), s -> Shapes.create(rotate(PLATE, s)));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return plateShape(state);
    }

    private static Map<Integer, Vec3> nodePositions(Level level, BlockPos pos, BlockState state) {
        var map = new HashMap<Integer, Vec3>();
        if(level.getBlockEntity(pos) instanceof BoardBlockEntity be) {
            var terminals = be.terminals();
            for(int i = 0; i < terminals.size(); i++)
                map.put(i, toLocal(state, terminals.get(i).getOrigin()));
        }
        return map;
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return nodePositions(level, pos, state);
    }

    @Override
    public @Nullable Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return nodePositions(level, pos, state).get(id);
    }

    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int id) {
        return 2 / 16f;
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        if(level.getBlockEntity(pos) instanceof BoardBlockEntity be && id >= 0 && id < be.terminals().size())
            return be.terminals().get(id).getName().copy();
        return super.getNodeLabel(level, pos, state, id);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        var device = DevicesSavedData.load(level).getDevice(pos, BoardDevice.class);
        if(device != null)
            device.registeredPins = getNodePositions(level, pos, state).size();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        var color = BoardColor.ofDye(stack);
        if(color == null || state.getValue(COLOR) == color || hitsPart(state, level, pos, hit))
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if(!level.isClientSide) {
            level.setBlock(pos, state.setValue(COLOR, color), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1, 1);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            stack.consume(1, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean hitsPart(BlockState state, Level level, BlockPos pos, BlockHitResult hit) {
        if(!(level.getBlockEntity(pos) instanceof BoardBlockEntity be))
            return false;
        var local = unrotate(hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()), getAngleX(state), getAngleY(state));
        for(var interactable : be.interactables()) {
            if(interactable.box().inflate(1 / 32f).contains(local))
                return true;
        }
        return false;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return use(state, level, pos, player, hit);
    }

    public static InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if(!(level.getBlockEntity(pos) instanceof BoardBlockEntity be))
            return InteractionResult.PASS;
        if(io.github.sjzeero.voltworks.utility.WiringTools.isWiringTool(player.getMainHandItem()))
            return InteractionResult.PASS;
        var local = unrotate(hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()), getAngleX(state), getAngleY(state));
        for(var interactable : be.interactables()) {
            if(!interactable.box().inflate(1 / 32f).contains(local))
                continue;
            if(interactable.clientScreen()) {
                if(level.isClientSide)
                    io.github.sjzeero.voltworks.client.ClientHooks.openComponentScreen(be, interactable.uuid());
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if(level instanceof ServerLevel server) {
                var device = DevicesSavedData.load(server).getDevice(pos, BoardDevice.class);
                if(device != null)
                    device.queueUse(interactable.uuid(), player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(player.isCreative() && player.isShiftKeyDown() && level instanceof ServerLevel server) {
            var device = DevicesSavedData.load(server).getDevice(pos, BoardDevice.class);
            if(device != null)
                device.queueRepair();
            return InteractionResult.SUCCESS;
        }
        if(player.isCreative() && !player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
            if(player instanceof ServerPlayer sp) {
                sp.openMenu(new SimpleMenuProvider((id, inv, p) -> p.isCreative() ? CircuitBoardEditMenu.create(id, inv, be) : null,
                        Component.translatable("block.voltworks.circuit_board")), be::sendToMenu);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if(level instanceof ServerLevel server) {
            var device = DevicesSavedData.load(server).getDevice(pos, BoardDevice.class);
            if(device != null)
                return device.emitted(direction.getOpposite());
        }
        return 0;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
        if(!(level instanceof ServerLevel server))
            return;
        var delta = fromPos.subtract(pos);
        var dir = Direction.fromDelta(delta.getX(), delta.getY(), delta.getZ());
        if(dir == null)
            return;
        var device = DevicesSavedData.load(server).getDevice(pos, BoardDevice.class);
        if(device != null)
            device.queueRedstone(getOrientation(state, dir), level.getSignal(fromPos, dir));
    }

    public static Direction getDirection(BlockState state, Orientation orientation) {
        var facing = state.getValue(HORIZONTAL_FACING);
        if(state.getValue(ROTATION) == 1) {
            if(orientation == Orientation.UP)
                return Direction.UP;
            if(orientation == Orientation.DOWN)
                return Direction.DOWN;
            if(orientation == Orientation.LEFT)
                return facing.getCounterClockWise();
            if(orientation == Orientation.RIGHT)
                return facing.getClockWise();
            throw new IllegalStateException();
        }
        Direction dir;
        if(state.getValue(ROTATION) == 0) {
            dir = switch(orientation) {
                case UP -> Direction.NORTH;
                case RIGHT -> Direction.EAST;
                case DOWN -> Direction.SOUTH;
                case LEFT -> Direction.WEST;
            };
            dir = switch(facing) {
                case NORTH -> dir;
                case SOUTH -> dir.getOpposite();
                case EAST -> dir.getClockWise();
                case WEST -> dir.getCounterClockWise();
                default -> throw new IllegalStateException();
            };
        } else {
            dir = switch(orientation) {
                case UP -> Direction.SOUTH;
                case RIGHT -> Direction.EAST;
                case DOWN -> Direction.NORTH;
                case LEFT -> Direction.WEST;
            };
            dir = switch(facing) {
                case NORTH -> dir.getOpposite();
                case SOUTH -> dir;
                case EAST -> dir.getCounterClockWise();
                case WEST -> dir.getClockWise();
                default -> throw new IllegalStateException();
            };
        }
        return dir;
    }

    @Nullable
    public static Orientation getOrientation(BlockState state, Direction side) {
        var facing = state.getValue(HORIZONTAL_FACING);
        if(state.getValue(ROTATION) == 1) {
            if(side == Direction.UP)
                return Orientation.UP;
            if(side == Direction.DOWN)
                return Orientation.DOWN;
            if(side == facing.getCounterClockWise())
                return Orientation.LEFT;
            if(side == facing.getClockWise())
                return Orientation.RIGHT;
            return null;
        }
        Orientation orientation;
        if(state.getValue(ROTATION) == 0) {
            orientation = switch(side) {
                case NORTH -> Orientation.UP;
                case EAST -> Orientation.RIGHT;
                case SOUTH -> Orientation.DOWN;
                case WEST -> Orientation.LEFT;
                default -> null;
            };
        } else {
            orientation = switch(side) {
                case NORTH -> Orientation.UP;
                case EAST -> Orientation.LEFT;
                case SOUTH -> Orientation.DOWN;
                case WEST -> Orientation.RIGHT;
                default -> null;
            };
        }
        if(orientation == null)
            return null;
        if(state.getValue(ROTATION) == 0) {
            orientation = switch(facing) {
                case SOUTH -> orientation.getOpposite();
                case EAST -> orientation.getCounterClockwise();
                case WEST -> orientation.getClockwise();
                default -> orientation;
            };
        } else {
            orientation = switch(facing) {
                case SOUTH -> orientation.getOpposite();
                case WEST -> orientation.getCounterClockwise();
                case EAST -> orientation.getClockwise();
                default -> orientation;
            };
        }
        return orientation;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoardBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if(type != Voltworks.BOARD_BE.get())
            return null;
        if(level.isClientSide)
            return (l, p, s, be) -> BoardBlockEntity.clientTick(l, p, s, (BoardBlockEntity) be);
        return (l, p, s, be) -> BoardBlockEntity.serverTick(l, p, s, (BoardBlockEntity) be);
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
