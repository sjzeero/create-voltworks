package io.github.sjzeero.voltworks.battery;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import io.github.sjzeero.voltworks.registry.VoltworksBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * One battery block. Blocks joined into a bank all point at the bank's controller (its lowest north-west corner),
 * which holds the bank's size and energy; the other blocks hold no energy of their own. A block that is not in a bank
 * yet ({@link #formed} false, e.g. just placed) keeps the energy it was placed with until {@link BatteryBanks} joins it.
 * Bank logic after Create: Power Grid (patryk3211, Apache-2.0), modified for Voltworks by sjzeero.
 */
public class BatteryBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private @Nullable BlockPos controller;
    private int sx = 1, sy = 1, sz = 1;
    double energy = BatterySpec.INITIAL_SOC * BatterySpec.CAPACITY;
    boolean formed;
    /** Power the bank gave out last tick, W (negative while charging); controller only, for the goggles. */
    double power;
    private double syncedEnergy = -1, syncedPower;

    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(VoltworksBlockEntities.BATTERY_BE.get(), pos, state);
    }

    public BlockPos controllerPos() {
        return controller == null ? worldPosition : controller;
    }

    public boolean isController() {
        return controller == null || controller.equals(worldPosition);
    }

    public @Nullable BatteryBlockEntity controllerBE() {
        if (isController())
            return this;
        return level != null && level.isLoaded(controller) && level.getBlockEntity(controller) instanceof BatteryBlockEntity be ? be : null;
    }

    public int sizeX() { return sx; }
    public int sizeY() { return sy; }
    public int sizeZ() { return sz; }

    public int size() {
        return sx * sy * sz;
    }

    public double energy() {
        return energy;
    }

    public double capacity() {
        return BatterySpec.CAPACITY * size();
    }

    /** True when this block is in a bank whose controller is loaded and still covers this block. */
    public boolean validBank() {
        if (!formed)
            return false;
        BatteryBlockEntity c = controllerBE();
        if (c == null || !c.formed || !c.isController())
            return false;
        BlockPos o = c.worldPosition, p = worldPosition;
        return p.getX() >= o.getX() && p.getX() < o.getX() + c.sx && p.getY() >= o.getY() && p.getY() < o.getY() + c.sy
                && p.getZ() >= o.getZ() && p.getZ() < o.getZ() + c.sz;
    }

    /** This block's share of its bank's energy (what it keeps when the bank is broken up). */
    public double cellEnergy() {
        if (!formed)
            return energy;
        BatteryBlockEntity c = validBank() ? controllerBE() : null;
        if (c == null)
            return isController() ? energy : 0;
        return c.energy / c.size();
    }

    void setBank(BlockPos origin, int sx, int sy, int sz, double energy) {
        this.controller = origin.equals(worldPosition) ? null : origin.immutable();
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.energy = isController() ? energy : 0;
        this.formed = true;
        if (!isController())
            power = 0;
        sync();
    }

    /** Leaves the bank holding {@code energy} on its own (the block is being removed or is alone). */
    void makeSingle(double energy) {
        setBank(worldPosition, 1, 1, 1, energy);
    }

    void setEnergyFromSim(double energy, double power) {
        this.energy = energy;
        this.power = power;
        setChanged();
    }

    /** Sends the controller's energy to clients when it moved enough to matter for the goggles. */
    void syncIfChanged() {
        if (Math.abs(energy - syncedEnergy) > capacity() * 1.0E-4 || Math.abs(power - syncedPower) > 0.05)
            sync();
    }

    void sync() {
        setChanged();
        syncedEnergy = energy;
        syncedPower = power;
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        BatteryBlockEntity c = controllerBE();
        if (c == null)
            c = this;
        double cap = c.capacity();
        double soc = cap <= 0 ? 0 : BatterySpec.clampSoc(c.energy / cap);
        tooltip.add(Component.literal("    ").append(getBlockState().getBlock().getName()));
        tooltip.add(Component.literal(" ").append(Component.translatable("voltworks.battery.goggles.charge",
                String.format(Locale.ROOT, "%.1f", soc * 100), fmt(c.energy / 3600), fmt(cap / 3600))
                .withStyle(soc > 0.25 ? ChatFormatting.GREEN : soc > 0.05 ? ChatFormatting.GOLD : ChatFormatting.RED)));
        if (c.size() > 1)
            tooltip.add(Component.literal(" ").append(Component.translatable("voltworks.battery.goggles.bank",
                    c.sx, c.sy, c.sz, c.size()).withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal(" ").append(Component.translatable("voltworks.battery.goggles.voltage",
                String.format(Locale.ROOT, "%.1f", BatterySpec.voltage(c.energy, cap))).withStyle(ChatFormatting.GRAY)));
        if (Math.abs(c.power) >= 0.05)
            tooltip.add(Component.literal(" ").append(Component.translatable(c.power > 0 ? "voltworks.battery.goggles.discharging" : "voltworks.battery.goggles.charging",
                    fmt(Math.abs(c.power))).withStyle(c.power > 0 ? ChatFormatting.GOLD : ChatFormatting.AQUA)));
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("Energy", energy);
        tag.putBoolean("Formed", formed);
        if (controller != null)
            tag.put("Controller", NbtUtils.writeBlockPos(controller));
        tag.putIntArray("Size", new int[]{sx, sy, sz});
        tag.putDouble("Power", power);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        BlockPos oldController = controllerPos();
        int oldSize = size();
        energy = tag.getDouble("Energy");
        formed = tag.getBoolean("Formed");
        controller = NbtUtils.readBlockPos(tag, "Controller").orElse(null);
        int[] s = tag.getIntArray("Size");
        if (s.length == 3) {
            sx = Math.max(1, s[0]);
            sy = Math.max(1, s[1]);
            sz = Math.max(1, s[2]);
        }
        power = tag.getDouble("Power");
        // joined faces lose their borders: redraw this block when its bank changed
        if (level != null && level.isClientSide && (!oldController.equals(controllerPos()) || oldSize != size()))
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
