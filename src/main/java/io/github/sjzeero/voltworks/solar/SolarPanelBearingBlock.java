package io.github.sjzeero.voltworks.solar;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.DirectionalKineticElectricBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.util.WiringTools;
import java.util.HashMap;
import java.util.Map;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import io.github.sjzeero.voltworks.registry.VoltworksBlockEntities;
import io.github.sjzeero.voltworks.registry.VoltworksDevices;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Ported from Create: Power Grid (patryk3211, Apache-2.0) to Voltworks by sjzeero; electrical side reimplemented on CEE devices.
 * FACING points at the attached panels; the input shaft is on the opposite face.
 */
public class SolarPanelBearingBlock extends DirectionalKineticElectricBlock<SolarPanelBearingDevice> implements IBE<SolarPanelBearingBlockEntity> {
    private static final VoxelShaper SHAPER;
    static {
        SHAPER = VoxelShaper.forDirectional(Shapes.or(box(2, 2, 4, 14, 14, 16), box(1, 1, 0, 15, 15, 4)), Direction.NORTH);
        SHAPER.withVerticalShapes(Shapes.or(box(2, 0, 2, 14, 12, 14), box(1, 12, 1, 15, 16, 15)));
    }
    /** PG terminal centres for FACING=NORTH in pixels: 0 = + (red), 1 = - (blue). */
    private static final double[][] TERMINALS = {{10.5, 14.5, 11.5}, {5.5, 14.5, 11.5}};

    public SolarPanelBearingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPER.get(state.getValue(FACING));
    }

    private static Vec3 node(BlockState state, int id) {
        double[] t = TERMINALS[id == 1 ? 1 : 0];
        double lx = t[0] / 16 - .5, ly = t[1] / 16 - .5, lz = t[2] / 16 - .5;
        Direction f = state.getValue(FACING);
        Direction right, up;
        switch (f) {
            case UP -> { right = Direction.EAST; up = Direction.SOUTH; }
            case DOWN -> { right = Direction.EAST; up = Direction.NORTH; }
            default -> { right = f.getClockWise(); up = Direction.UP; }
        }
        var r = right.getNormal(); var u = up.getNormal(); var b = f.getOpposite().getNormal();
        return new Vec3(.5 + r.getX() * lx + u.getX() * ly + b.getX() * lz,
                .5 + r.getY() * lx + u.getY() * ly + b.getY() * lz,
                .5 + r.getZ() * lx + u.getZ() * ly + b.getZ() * lz);
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
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return Component.translatable("voltworks.node.solar_panel_" + id);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild() || player.isShiftKeyDown()) return InteractionResult.PASS;
        if (WiringTools.isWiringTool(player.getMainHandItem())) return InteractionResult.PASS;
        var behaviour = BlockEntityBehaviour.get(level, pos, SolarPanelBearingScrollBehaviour.TYPE);
        if (behaviour != null) {
            if (behaviour.getSlotPositioning() instanceof ValueBoxTransform.Sided sided) sided.fromSide(hit.getDirection());
            if (behaviour.testHit(hit.getLocation())) return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        withBlockEntityDo(level, pos, SolarPanelBearingBlockEntity::toggleAssembly);
        return InteractionResult.SUCCESS;
    }

    @Override
    public SimulatedDeviceType<SolarPanelBearingDevice> getDevice() {
        return VoltworksDevices.SOLAR_PANEL_BEARING_DEVICE.get();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
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
        if (preferred == null || sneak) {
            Direction look = context.getNearestLookingDirection();
            return defaultBlockState().setValue(FACING, sneak ? look : look.getOpposite());
        }
        return defaultBlockState().setValue(FACING, preferred);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (!context.getLevel().isClientSide && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof SolarPanelBearingBlockEntity be)
            be.disassemble();
        return super.onWrenched(state, context);
    }

    @Override
    public Class<SolarPanelBearingBlockEntity> getBlockEntityClass() {
        return SolarPanelBearingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SolarPanelBearingBlockEntity> getBlockEntityType() {
        return VoltworksBlockEntities.SOLAR_PANEL_BEARING_BE.get();
    }
}
