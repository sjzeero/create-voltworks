package io.github.sjzeero.voltworks.servo;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.foundation.RMSHolder;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.infrastructure.config.AllConfigs;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.motor.AbstractPGMotorBlockEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Create: Power Grid (patryk3211, Apache-2.0) to Voltworks by sjzeero; electrical side reimplemented on CEE devices.
 */
public class ServoBlockEntity extends GeneratingKineticBlockEntity {
    public static final float MAX_SPEED = 32f;
    public static final float STRESS_CAPACITY = 32f;
    public static final double ON_RESISTANCE = 12.8;
    public static final double IDLE_RESISTANCE = 64;
    public static final int AVERAGING_TICKS = 5;

    public final RMSHolder coilVoltage;
    public final RMSHolder controlVoltage;
    private float generatedSpeed;
    private int currentAngle;
    private int currentTarget;
    private int prevTarget;
    private int movingTicks;
    private float avgSpeed;
    private float maxSpeed;

    public ServoBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        int window = 3 * CEEConfigs.server().simulationConfig.microTicks.get();
        coilVoltage = new RMSHolder(window);
        controlVoltage = new RMSHolder(window);
        setLazyTickRate(AVERAGING_TICKS - 1);
    }

    public ServoBlockEntity(BlockPos pos, BlockState state) {
        this(Voltworks.SERVO_BE.get(), pos, state);
    }

    public double coilResistance() {
        return generatedSpeed != 0 ? ON_RESISTANCE : IDLE_RESISTANCE;
    }

    public float torque() {
        return STRESS_CAPACITY * AbstractPGMotorBlockEntity.TORQUE_FOR_STRESS;
    }

    /** PG: 5V is 360 degrees clock-wise. Servo has a [-5V, 5V] range. */
    public int controlTarget() {
        return Math.round(Mth.clamp((float) controlVoltage.getSigned() / 5.0f * 360.0f, -360, 360));
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide) return;
        maxSpeed = Math.min(avgSpeed / AVERAGING_TICKS, MAX_SPEED);
        avgSpeed = 0;
        if (maxSpeed == 0 && generatedSpeed != 0) {
            generatedSpeed = 0;
            updateGeneratedRotation();
            notifyUpdate();
        }
    }

    @Override
    public void tick() {
        boolean server = level != null && (!level.isClientSide || isVirtual());
        if (server) {
            double u = coilVoltage.getSigned();
            avgSpeed += (float) AbstractPGMotorBlockEntity.calculateSpeed(u * u / coilResistance(), torque());
            int ctrl = controlTarget();
            if (prevTarget == ctrl) currentTarget = ctrl;
            prevTarget = ctrl;
        }
        super.tick();
        if (!server) return;
        // Precision of 1 degree (integer angles only)
        int rotation = currentTarget - currentAngle;
        if (Math.abs(rotation) < 1) rotation = 0;
        if (movingTicks > 0) {
            if (--movingTicks == 0) {
                generatedSpeed = 0;
                sequenceContext = null;
                updateGeneratedRotation();
                notifyUpdate();
            }
            return;
        }
        if (rotation != 0) {
            generatedSpeed = (int) Mth.clamp(rotation / 0.05f / 6, -maxSpeed, maxSpeed);
            if (generatedSpeed == 0) return; // guard: PG divides by zero here and locks up
            sequenceContext = new SequencedGearshiftBlockEntity.SequenceContext(SequencerInstructions.TURN_ANGLE, rotation / generatedSpeed);
            updateGeneratedRotation();
            notifyUpdate();
            currentAngle = currentTarget;
            movingTicks = (int) Math.abs(rotation / KineticBlockEntity.convertToAngular(generatedSpeed)) + 5;
        }
    }

    @Override
    public float getGeneratedSpeed() {
        return convertToDirection(generatedSpeed, getBlockState().getValue(ServoBlock.FACING));
    }

    @Override
    public float calculateAddedStressCapacity() {
        this.lastCapacityProvided = STRESS_CAPACITY;
        return STRESS_CAPACITY;
    }

    @Override
    public int getFlickerScore() {
        return 0;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("GeneratedSpeed", generatedSpeed);
        tag.putInt("Angle", currentAngle);
        tag.putInt("Target", currentTarget);
        tag.putInt("MovingTicks", movingTicks);
        coilVoltage.write(tag, "Voltage");
        controlVoltage.write(tag, "ControlVoltage");
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        generatedSpeed = tag.getFloat("GeneratedSpeed");
        currentAngle = tag.getInt("Angle");
        currentTarget = tag.contains("Target") ? tag.getInt("Target") : currentAngle;
        prevTarget = currentTarget;
        movingTicks = tag.getInt("MovingTicks");
        coilVoltage.read(tag, "Voltage");
        controlVoltage.read(tag, "ControlVoltage");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.literal("    ").append(Component.translatable("gui.goggles.voltworks.motor.voltage").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("    ").append(Component.literal(String.format("%.1f V", coilVoltage.getSigned())).withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.literal("    ").append(Component.translatable("gui.goggles.voltworks.servo.control").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("    ").append(Component.literal(String.format("%.2f V  (%d\u00b0)", controlVoltage.getSigned(), currentAngle)).withStyle(ChatFormatting.GREEN)));
        return true;
    }
}
