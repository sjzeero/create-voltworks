package io.github.sjzeero.voltworks.battery;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.simibubi.create.foundation.block.IBE;
import io.github.sjzeero.voltworks.registry.VoltworksBlockEntities;
import io.github.sjzeero.voltworks.registry.VoltworksDevices;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A full-block rechargeable battery, ported from Create: Power Grid's {@code powergrid:battery} (patryk3211,
 * Apache-2.0) its look adapted from Power Grid's battery textures by sjzeero. Touching batteries join into banks ({@link BatteryBanks}); the bank's top corner block
 * carries the + and - terminal posts, every block of the bank is wired to the same storage.
 */
public class BatteryBlock extends SimpleElectricalDeviceBlock<BatteryDevice> implements IBE<BatteryBlockEntity> {
    /** Gauge marks shown in this block's window. */
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, BatterySpec.GAUGE_LEVELS);
    /** True on the bank's top north-west block, which carries the terminal posts. */
    public static final BooleanProperty TERMINALS = BooleanProperty.create("terminals");

    /** Tops of the terminal posts standing on the block, in sixteenths: + (copper) on the west, - (iron) on the east. */
    public static final Vec3 PLUS = new Vec3(5 / 16.0, 18 / 16.0, 8 / 16.0);
    public static final Vec3 MINUS = new Vec3(11 / 16.0, 18 / 16.0, 8 / 16.0);
    public static final String ENERGY_TAG = "Energy";

    public BatteryBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CHARGE, 0).setValue(TERMINALS, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(CHARGE, TERMINALS));
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return Map.of(0, PLUS, 1, MINUS);
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return id == 1 ? MINUS : PLUS;
    }

    /** Terminals stay registered on every block of a bank (wires already on them keep working); only the post block's can be reached. */
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

    /** Energy a battery item carries; a battery that was never placed is {@link BatterySpec#INITIAL_SOC} charged. */
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

    /** A broken battery keeps its share of the bank's energy. */
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
