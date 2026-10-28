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
package io.github.sjzeero.voltworks.battery;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.simibubi.create.foundation.block.IBE;
import io.github.sjzeero.voltworks.registry.VoltworksBlockEntities;
import io.github.sjzeero.voltworks.registry.VoltworksDevices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class BatteryBlock extends SimpleElectricalDeviceBlock<BatteryDevice> implements IBE<BatteryBlockEntity> {
    public static final BooleanProperty TERMINALS = BooleanProperty.create("terminals");
    public static final Map<Direction, BooleanProperty> JOINED = Map.of(
            Direction.NORTH, BlockStateProperties.NORTH, Direction.SOUTH, BlockStateProperties.SOUTH,
            Direction.EAST, BlockStateProperties.EAST, Direction.WEST, BlockStateProperties.WEST,
            Direction.UP, BlockStateProperties.UP, Direction.DOWN, BlockStateProperties.DOWN);

    public static final Vec3 PLUS = new Vec3(5 / 16.0, 18 / 16.0, 8 / 16.0);
    public static final Vec3 MINUS = new Vec3(11 / 16.0, 18 / 16.0, 8 / 16.0);
    public static final String ENERGY_TAG = "Energy";

    public BatteryBlock(Properties properties) {
        super(properties);
        BlockState s = defaultBlockState().setValue(TERMINALS, true);
        for (BooleanProperty p : JOINED.values())
            s = s.setValue(p, false);
        registerDefaultState(s);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TERMINALS);
        for (Direction d : Direction.values())
            builder.add(JOINED.get(d));
        super.createBlockStateDefinition(builder);
    }

    public static BlockState withBankLook(BlockState s, boolean terminals, int x, int y, int z, int sx, int sy, int sz) {
        s = s.setValue(TERMINALS, terminals);
        for (Direction d : Direction.values())
            s = s.setValue(JOINED.get(d), BankPartition.joined(x, y, z, sx, sy, sz, d));
        return s;
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return Map.of(0, PLUS, 1, MINUS);
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return id == 1 ? MINUS : PLUS;
    }

    @Override
    public boolean isNodeAccessible(Level level, BlockPos pos, BlockState state, int id) {
        return state.getValue(TERMINALS);
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return Component.translatable("voltworks.node.battery_" + (id == 1 ? 1 : 0));
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        BatteryBanks.ensure(level, pos);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BatteryBlockEntity be) {
            be.energy = storedEnergy(stack);
            be.formed = false;
            be.setChanged();
        }
    }

    public static double storedEnergy(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(ENERGY_TAG))
            return BatterySpec.INITIAL_SOC * BatterySpec.CAPACITY;
        return Math.max(0, Math.min(BatterySpec.CAPACITY, data.copyTag().getDouble(ENERGY_TAG)));
    }

    public static void setStoredEnergy(ItemStack stack, double energy) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putDouble(ENERGY_TAG, Math.max(0, Math.min(BatterySpec.CAPACITY, energy)));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof BatteryBlockEntity battery)
            for (ItemStack s : drops)
                if (s.is(asItem()))
                    setStoredEnergy(s, battery.cellEnergy());
        return drops;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof BatteryBlockEntity be)
            BatteryBanks.removed(level, pos, be);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof BatteryBlockEntity be) {
            BatteryBlockEntity c = be.controllerBE();
            if (c != null)
                return BatterySpec.comparator(c.energy(), c.capacity());
        }
        return 0;
    }

    @Override
    public SimulatedDeviceType<BatteryDevice> getDevice() {
        return VoltworksDevices.BATTERY_DEVICE.get();
    }

    @Override
    public Class<BatteryBlockEntity> getBlockEntityClass() {
        return BatteryBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BatteryBlockEntity> getBlockEntityType() {
        return VoltworksBlockEntities.BATTERY_BE.get();
    }
}
