package io.github.sjzeero.voltworks.motor;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.foundation.RMSHolder;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Ported from Create: Power Grid (patryk3211) to Voltworks by sjzeero; electrical side reimplemented on CEE devices.
 * Shared electrical model of the PG motors: coil resistance R / load, speed = (U^2 / R) / torque * 60pi/2.
 */
public abstract class AbstractPGMotorBlockEntity extends GeneratingKineticBlockEntity {
    public static final int AVERAGING_TICKS = 5;
    public static final float CONVERSION_CONSTANT = (float) (60 * Math.PI / 2);
    public static final float STRESS_CAPACITY = 64f;
    public static final double COIL_RESISTANCE = 25.6;
    public static final float TORQUE_FOR_STRESS = 15f;
    public static final float MIN_LOAD = 0.05f;

    public final RMSHolder voltage;
    protected float load = MIN_LOAD;
    protected float avgSpeed;

    protected AbstractPGMotorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.voltage = new RMSHolder(3 * CEEConfigs.server().simulationConfig.microTicks.get());
        setLazyTickRate(AVERAGING_TICKS - 1);
    }

    public static int maxRPM() {
        return AllConfigs.server().kinetics.maxRotationSpeed.get();
    }

    public float torque() {
        return STRESS_CAPACITY * TORQUE_FOR_STRESS;
    }

    public static double calculateSpeed(double power, double torque) {
        return power / torque * CONVERSION_CONSTANT;
    }

    /** Resistance the coil presents to the CEE grid this tick. */
    public double coilResistance() {
        return COIL_RESISTANCE / load;
    }

    /** Signed speed the electrical power alone would produce, clamped to the Create speed limit. */
    protected int electricalSpeed() {
        int s = (int) (avgSpeed / AVERAGING_TICKS);
        return Mth.clamp(s, -maxRPM(), maxRPM());
    }

    @Override
    public void updateFromNetwork(float maxStress, float currentStress, int networkSize) {
        super.updateFromNetwork(maxStress, currentStress, networkSize);
        load = maxStress != 0 ? Mth.clamp(currentStress / maxStress, MIN_LOAD, 1f) : MIN_LOAD;
    }

    @Override
    public void tick() {
        if (level != null && (!level.isClientSide || isVirtual())) {
            double u = voltage.getSigned();
            avgSpeed += (float) (calculateSpeed(u * u / COIL_RESISTANCE, torque()) * Math.signum(u));
        }
        super.tick();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        int newSpeed = electricalSpeed();
        avgSpeed = 0;
        if (level != null && (!level.isClientSide || isVirtual())) onElectricalSpeed(newSpeed);
    }

    protected abstract void onElectricalSpeed(int speed);

    /** Voltage fluctuations must not trip Create's flicker protection (PG solved this with a mixin hack). */
    @Override
    public int getFlickerScore() {
        return 0;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        voltage.write(tag, "Voltage");
        tag.putFloat("Load", load);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        voltage.read(tag, "Voltage");
        if (tag.contains("Load")) load = tag.getFloat("Load");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.literal("    ").append(Component.translatable("gui.goggles.voltworks.motor.voltage").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("    ").append(Component.literal(String.format("%.1f V", voltage.getSigned())).withStyle(ChatFormatting.AQUA)));
        return true;
    }
}
