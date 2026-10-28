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

public abstract class AbstractPGMotorBlockEntity extends GeneratingKineticBlockEntity {
    public static final int AVERAGING_TICKS = 5;
    public static final float CONVERSION_CONSTANT = (float) (60 * Math.PI / 2);
    public static final float STRESS_CAPACITY = 64f;
    public static final float TORQUE_FOR_STRESS = 15f;
    public static final float MIN_LOAD = (float) MotorPower.MIN_LOAD;

    public final RMSHolder voltage;
    protected float load = MIN_LOAD;
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

    public static double motorResistance() {
        try {
            return CEEConfigs.server().resistanceValues.motorResistance.get();
        } catch (RuntimeException | LinkageError e) {
            return MotorPower.DEFAULT_MOTOR_RESISTANCE;
        }
    }

    public double coilResistance() {
        return MotorPower.coilResistance(motorResistance(), load);
    }

    public double inputPower() {
        return MotorPower.inputPower(voltage.getSigned(), motorResistance(), load);
    }

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

    protected abstract void onElectricalStress(float stress);

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
