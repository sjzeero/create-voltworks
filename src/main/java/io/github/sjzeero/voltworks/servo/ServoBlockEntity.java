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
package io.github.sjzeero.voltworks.servo;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.foundation.RMSHolder;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
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
import io.github.sjzeero.voltworks.registry.VoltworksBlockEntities;

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
    private int settleTicks;
    private float avgSpeed;
    private float maxSpeed;
    public volatile double dbgV0, dbgV1, dbgV2;
    private double syncV0, syncV1, syncV2, syncCtrl;

    public ServoBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        int window = 3 * CEEConfigs.server().simulationConfig.microTicks.get();
        coilVoltage = new RMSHolder(window);
        controlVoltage = new RMSHolder(window);
        setLazyTickRate(AVERAGING_TICKS - 1);
    }

    public ServoBlockEntity(BlockPos pos, BlockState state) {
        this(VoltworksBlockEntities.SERVO_BE.get(), pos, state);
    }

    public double coilResistance() {
        return generatedSpeed != 0 ? ON_RESISTANCE : IDLE_RESISTANCE;
    }

    public float torque() {
        return STRESS_CAPACITY * AbstractPGMotorBlockEntity.TORQUE_FOR_STRESS;
    }

    public int controlTarget() {
        return Math.round(Mth.clamp((float) controlVoltage.getSigned() / 5.0f * 360.0f, -360, 360));
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide) return;
        maxSpeed = Math.min(avgSpeed / AVERAGING_TICKS, MAX_SPEED);
        avgSpeed = 0;
        if (Math.abs(dbgV0 - syncV0) > 0.05 || Math.abs(dbgV1 - syncV1) > 0.05 || Math.abs(dbgV2 - syncV2) > 0.05
                || Math.abs(controlVoltage.getSigned() - syncCtrl) > 0.01) {
            syncV0 = dbgV0; syncV1 = dbgV1; syncV2 = dbgV2; syncCtrl = controlVoltage.getSigned();
            sendData();
        }
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
            avgSpeed += (float) AbstractPGMotorBlockEntity.calculateSpeed(u * u / ON_RESISTANCE, torque());
            int ctrl = controlTarget();
            if (settleTicks > 0) settleTicks--;
            if (movingTicks == 0 && settleTicks == 0 && prevTarget == ctrl && Math.abs(ctrl - currentAngle) >= 2) currentTarget = ctrl;
            prevTarget = ctrl;
        }
        super.tick();
        if (!server) return;
        int rotation = currentTarget - currentAngle;
        if (Math.abs(rotation) < 1) rotation = 0;
        if (level.getGameTime() % 20 == 0 && (generatedSpeed != 0 || movingTicks > 0)) {
            System.out.println("[servo-dbg] pos=" + worldPosition + " genSpeed=" + generatedSpeed + " moving=" + movingTicks + " settle=" + settleTicks + " target=" + currentTarget + " angle=" + currentAngle + " ctrl=" + controlTarget() + " prev=" + prevTarget + " maxSpeed=" + maxSpeed + " kSpeed=" + getSpeed());
        }
        if (movingTicks > 0) {
            if (--movingTicks == 0) {
                settleTicks = 10;
                generatedSpeed = 0;
                sequenceContext = null;
                updateGeneratedRotation();
                notifyUpdate();
            }
            return;
        }
        if (rotation != 0) {
            generatedSpeed = (int) Mth.clamp(rotation / 0.05f / 6, -maxSpeed, maxSpeed);
            if (generatedSpeed == 0) return;
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
        tag.putDouble("DbgV0", dbgV0);
        tag.putDouble("DbgV1", dbgV1);
        tag.putDouble("DbgV2", dbgV2);
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
        dbgV0 = tag.getDouble("DbgV0");
        dbgV1 = tag.getDouble("DbgV1");
        dbgV2 = tag.getDouble("DbgV2");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.literal("    ").append(Component.translatable("gui.goggles.voltworks.motor.voltage").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("    ").append(Component.literal(String.format("%.1f V", coilVoltage.getSigned())).withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.literal("    ").append(Component.translatable("gui.goggles.voltworks.servo.control").withStyle(ChatFormatting.GRAY)));
        double ctrlV = controlVoltage.getSigned();
        tooltip.add(Component.literal(String.format("    %.2f V -> target %d deg", ctrlV, currentTarget)).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(String.format("    now %d deg", currentAngle)).withStyle(ChatFormatting.GREEN));
        if (Math.abs(ctrlV) > 5.0) {
            tooltip.add(Component.literal("    Control is above 5 V: wire the control supply to the servo minus pin").withStyle(ChatFormatting.RED));
        }
        return true;
    }
}
