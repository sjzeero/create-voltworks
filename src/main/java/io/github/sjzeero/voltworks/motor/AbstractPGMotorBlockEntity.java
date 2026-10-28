package io.github.sjzeero.voltworks.motor;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.foundation.CEELang;
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
import io.github.sjzeero.voltworks.Voltworks;

/**
 * Ported from Create: Power Grid (patryk3211) to Voltworks by sjzeero; electrical side reimplemented on CEE devices.
 * Electrical model follows Create: Electro Energetics' own electric motor (see {@link MotorPower}): the rated power
 * U^2 / R (R = EE's motorResistance config) becomes stress at 1 W -> 1 SU, and the coil draws
 * U^2 / (0.8 * min(6 R, R / load)), i.e. 1.25 W per SU used, never under the 1/6-load idle draw.
 */
public abstract class AbstractPGMotorBlockEntity extends GeneratingKineticBlockEntity {
    public static final int AVERAGING_TICKS = 5;
    /** PG's power -> speed constant; kept for the servo, which still uses PG's speed model. */
    public static final float CONVERSION_CONSTANT = (float) (60 * Math.PI / 2);
    /** Stress step (SU) the supplied power is rounded down to; also the least stress the motor starts with. */
    public static final float STRESS_CAPACITY = 64f;
    public static final float TORQUE_FOR_STRESS = 15f;
    public static final float MIN_LOAD = (float) MotorPower.MIN_LOAD;

    public final RMSHolder voltage;
    protected float load = MIN_LOAD;
    /** Signed rated stress (SU) summed over the averaging window. */
    protected float avgStress;

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

    /** EE's motorResistance server config (30 ohm by default): the rated resistance of EE's own motors. */
    public static double motorResistance() {
        try {
            return CEEConfigs.server().resistanceValues.motorResistance.get();
        } catch (RuntimeException | LinkageError e) {
            return MotorPower.DEFAULT_MOTOR_RESISTANCE;
        }
    }

    /** Resistance the coil presents to the CEE grid this tick (EE motor: 0.8 * min(6 R, R / load)). */
    public double coilResistance() {
        return MotorPower.coilResistance(motorResistance(), load);
    }

    /** Electrical power drawn from the grid at the current voltage and load, in watts. */
    public double inputPower() {
        return MotorPower.inputPower(voltage.getSigned(), motorResistance(), load);
    }

    /** Signed stress the averaged electrical power provides (1 W -> 1 SU), rounded down to STRESS_CAPACITY steps. */
    protected float electricalStress() {
        return quantizeStress(avgStress / AVERAGING_TICKS);
    }

    public static float quantizeStress(double stress) {
        return (float) MotorPower.quantize(stress, STRESS_CAPACITY);
    }

    @Override
    public void updateFromNetwork(float maxStress, float currentStress, int networkSize) {
        super.updateFromNetwork(maxStress, currentStress, networkSize);
        float old = load;
        load = maxStress != 0 ? Mth.clamp(currentStress / maxStress, MIN_LOAD, 1f) : MIN_LOAD;
        if (Math.abs(old - load) > 0.01f) sendData();
    }

    @Override
    public void tick() {
        if (level != null && (!level.isClientSide || isVirtual())) {
            double u = voltage.getSigned();
            avgStress += (float) (MotorPower.ratedStress(u, motorResistance()) * Math.signum(u));
        }
        super.tick();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        float newStress = electricalStress();
        avgStress = 0;
        if (level != null && (!level.isClientSide || isVirtual())) onElectricalStress(newStress);
    }

    /** Signed stress (SU, capacity x RPM) the supplied power provides; the sign is the supply polarity. */
    protected abstract void onElectricalStress(float stress);

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
        CEELang.builder().translate("gui.goggles.energy_consumption").style(ChatFormatting.GRAY).forGoggles(tooltip);
        CEELang.builder().add(CEELang.formatPower(Math.round(inputPower()))).style(ChatFormatting.AQUA).space()
                .add(CEELang.translate("gui.goggles.at_current_load").style(ChatFormatting.DARK_GRAY)).forGoggles(tooltip, 1);
        return true;
    }
}
