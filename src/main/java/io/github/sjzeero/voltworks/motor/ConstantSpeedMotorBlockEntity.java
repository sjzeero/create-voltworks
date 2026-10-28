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

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import io.github.sjzeero.voltworks.Voltworks;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import io.github.sjzeero.voltworks.registry.VoltworksBlockEntities;

public class ConstantSpeedMotorBlockEntity extends AbstractPGMotorBlockEntity {
    protected ScrollValueBehaviour targetSpeed;
    protected float generatedSU;

    public ConstantSpeedMotorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ConstantSpeedMotorBlockEntity(BlockPos pos, BlockState state) {
        this(VoltworksBlockEntities.CONSTANT_SPEED_MOTOR_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        targetSpeed = new ScrollValueBehaviour(Component.translatable("voltworks.motor.speed"), this, new Box());
        targetSpeed.between(1, maxRPM());
        targetSpeed.value = 16;
        targetSpeed.withCallback(i -> updateGeneratedRotation());
        behaviours.add(targetSpeed);
    }

    @Override
    protected void onElectricalStress(float su) {
        if (su != generatedSU) {
            generatedSU = su;
            updateGeneratedRotation();
        }
    }

    private boolean running() {
        return Math.abs(generatedSU) >= STRESS_CAPACITY && targetSpeed != null && targetSpeed.getValue() > 0;
    }

    @Override
    public float getGeneratedSpeed() {
        if (!running()) return 0;
        return convertToDirection(targetSpeed.getValue() * (generatedSU < 0 ? -1 : 1), getBlockState().getValue(ConstantSpeedMotorBlock.FACING));
    }

    @Override
    public float calculateAddedStressCapacity() {
        float c = running() ? Math.abs(generatedSU) / targetSpeed.getValue() : 0;
        this.lastCapacityProvided = c;
        return c;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("GeneratedStress", generatedSU);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        generatedSU = tag.getFloat("GeneratedStress");
    }

    public static class Box extends CenteredSideValueBoxTransform {
        public Box() {
            super((state, dir) -> {
                Direction facing = state.getValue(ConstantSpeedMotorBlock.FACING);
                if (facing.getAxis() == Direction.Axis.Y) return dir.getAxis() == Direction.Axis.Z;
                return dir == Direction.UP;
            });
        }

        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8.0f, 8.0f, 12.5f);
        }
    }
}
