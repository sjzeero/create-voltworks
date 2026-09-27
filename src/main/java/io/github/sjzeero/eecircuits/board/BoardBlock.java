package io.github.sjzeero.eecircuits.board;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import io.github.sjzeero.eecircuits.EECircuits;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class BoardBlock extends SimpleElectricalDeviceBlock<BoardDevice> {
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 3, 16);
    private static final Map<Integer, Vec3> PINS = Map.of(
            0, new Vec3(3 / 16d, 4 / 16d, 8 / 16d),
            1, new Vec3(13 / 16d, 4 / 16d, 8 / 16d));

    public BoardBlock(Properties properties) {
        super(properties);
    }

    @Override
    public SimulatedDeviceType<BoardDevice> getDevice() {
        return EECircuits.BOARD_DEVICE.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return PINS;
    }

    @Override
    public @Nullable Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return PINS.get(id);
    }

    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int id) {
        return 2 / 16f;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
