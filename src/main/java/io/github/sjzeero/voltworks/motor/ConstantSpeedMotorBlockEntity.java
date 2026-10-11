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

/**
 * Ported from Create: Power Grid (patryk3211) to Voltworks by sjzeero; electrical side reimplemented on CEE devices.
 * Speed is set with the value box; the supplied power becomes stress capacity, its polarity the direction.
 */
public class ConstantSpeedMotorBlockEntity extends AbstractPGMotorBlockEntity {
    protected ScrollValueBehaviour targetSpeed;
    protected float generatedSU;

    public ConstantSpeedMotorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ConstantSpeedMotorBlockEntity(BlockPos pos, BlockState state) {
        this(Voltworks.CONSTANT_SPEED_MOTOR_BE.get(), pos, state);
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
    protected void onElectricalSpeed(int speed) {
        float su = speed * STRESS_CAPACITY;
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
