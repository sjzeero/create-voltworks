package io.github.sjzeero.voltworks.light;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import io.github.sjzeero.voltworks.registry.VoltworksDevices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/** Voltage sensor: a 10 kOhm load between node 0 (+) and node 1 (-) that emits redstone 0..15 for 0..48 V. */
public class VoltageSensorBlock extends SimpleElectricalDeviceBlock<VoltageSensorDevice> {
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 8, 14);

    public VoltageSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWER);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public SimulatedDeviceType<VoltageSensorDevice> getDevice() {
        return VoltworksDevices.VOLTAGE_SENSOR_DEVICE.get();
    }

    private static Vec3 node(int id) {
        return new Vec3(id == 0 ? 4 / 16.0 : 12 / 16.0, 4 / 16.0, 0.5);
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return Map.of(0, node(0), 1, node(1));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return node(id);
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return Component.translatable("voltworks.node.voltage_sensor_" + id);
    }
}
