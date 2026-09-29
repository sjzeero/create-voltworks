package io.github.sjzeero.voltworks.servo;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.foundation.CEELang;
import com.george_vi.electroenergetics.foundation.RMSHolder;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.IBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.sjzeero.voltworks.Voltworks;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class ServoBlockEntity extends GeneratingKineticBlockEntity
        implements IBearingBlockEntity, IDisplayAssemblyExceptions {
    protected ScrollOptionBehaviour<IControlContraption.RotationMode> movementMode;
    protected ControlledContraptionEntity movedContraption;
    public float angle;
    protected float prevAngle;
    public float targetAngle;
    public boolean running;
    public boolean assembleNextTick;
    protected float clientAngleDiff;
    protected AssemblyException lastException;
    public final RMSHolder averageVoltage;

    public ServoBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.averageVoltage = new RMSHolder(3 * CEEConfigs.server().simulationConfig.microTicks.get());
    }

    public ServoBlockEntity(BlockPos pos, BlockState state) {
        this(Voltworks.SERVO_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        this.movementMode = new ScrollOptionBehaviour<>(IControlContraption.RotationMode.class,
                CreateLang.translateDirect("contraptions.movement_mode"), this, this.getMovementModeSlot());
        behaviours.add(this.movementMode);
    }

    @Override
    public boolean isWoodenTop() {
        return false;
    }

    @Override
    public void setAngle(float angle) {
        this.angle = angle;
    }

    @Override
    public float getInterpolatedAngle(float partialTicks) {
        if (this.isVirtual()) {
            return Mth.lerp(partialTicks + 0.5f, this.prevAngle, this.angle);
        }
        return Mth.lerp(partialTicks, this.prevAngle, this.angle);
    }

    @Override
    public boolean isAttachedTo(AbstractContraptionEntity entity) {
        return this.movedContraption == entity;
    }

    @Override
    public void attach(ControlledContraptionEntity contraption) {
        BlockState blockState = this.getBlockState();
        if (!(contraption.getContraption() instanceof BearingContraption)) {
            return;
        }
        if (!(blockState.getBlock() instanceof ServoBlock)) {
            return;
        }
        this.movedContraption = contraption;
        this.setChanged();
        BlockPos anchor = this.worldPosition.relative(blockState.getValue(ServoBlock.FACING));
        this.movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        if (this.level != null && !this.level.isClientSide) {
            this.running = true;
            this.sendData();
        }
    }

    @Override
    public void onStall() {
        if (this.level != null && !this.level.isClientSide) {
            this.sendData();
        }
    }

    @Override
    public boolean isValid() {
        return !this.isRemoved();
    }

    @Override
    public BlockPos getBlockPosition() {
        return this.worldPosition;
    }

    @Override
    public AssemblyException getLastAssemblyException() {
        return this.lastException;
    }

    public void assemble() {
        if (this.level == null || !(this.level.getBlockState(this.worldPosition).getBlock() instanceof ServoBlock)) {
            return;
        }
        Direction direction = this.getBlockState().getValue(ServoBlock.FACING);
        BearingContraption contraption = new BearingContraption(false, direction);
        try {
            if (!contraption.assemble(this.level, this.worldPosition)) {
                return;
            }
            this.lastException = null;
        } catch (AssemblyException e) {
            this.lastException = e;
            this.sendData();
            return;
        }
        contraption.removeBlocksFromWorld(this.level, BlockPos.ZERO);
        this.movedContraption = ControlledContraptionEntity.create(this.level, this, contraption);
        BlockPos anchor = this.worldPosition.relative(direction);
        this.movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        this.movedContraption.setRotationAxis(direction.getAxis());
        this.level.addFreshEntity(this.movedContraption);
        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(this.level, this.worldPosition);
        this.running = true;
        this.angle = 0.0f;
        this.prevAngle = 0.0f;
        this.sendData();
        this.updateGeneratedRotation();
    }

    public void disassemble() {
        if (!this.running && this.movedContraption == null) {
            return;
        }
        this.angle = 0.0f;
        this.prevAngle = 0.0f;
        if (this.movedContraption != null) {
            this.movedContraption.disassemble();
            if (this.level != null) {
                AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(this.level, this.worldPosition);
            }
        }
        this.movedContraption = null;
        this.running = false;
        this.updateGeneratedRotation();
        this.assembleNextTick = false;
        this.sendData();
    }

    @Override
    public void tick() {
        super.tick();
        this.prevAngle = this.angle;
        if (this.level == null) return;

        if (this.level.isClientSide) {
            boolean clientPowered = this.averageVoltage.get() >= 10.0;
            if (clientPowered) {
                float diff = this.targetAngle - this.angle;
                if (Math.abs(diff) > 0.01f) {
                    float step = 6.0f;
                    float move = Math.signum(diff) * Math.min(Math.abs(diff), step);
                    this.angle += move;
                }
            }
            if (this.movedContraption != null) {
                this.applyRotation();
            }
            return;
        }

        if (this.assembleNextTick) {
            this.assembleNextTick = false;
            if (this.running) {
                boolean canDisassemble = this.movementMode.get() == IControlContraption.RotationMode.ROTATE_PLACE ||
                        (this.isNearInitialAngle() && this.movementMode.get() == IControlContraption.RotationMode.ROTATE_PLACE_RETURNED);
                if (canDisassemble || this.movedContraption == null || this.movedContraption.getContraption().getBlocks().isEmpty()) {
                    if (this.movedContraption != null) {
                        this.movedContraption.getContraption().stop(this.level);
                    }
                    this.disassemble();
                    return;
                }
            } else {
                this.assemble();
            }
        }

        int redstonePower = this.level.getBestNeighborSignal(this.worldPosition);
        float newTargetAngle = (redstonePower / 15.0f) * 180.0f;
        boolean targetChanged = Math.abs(newTargetAngle - this.targetAngle) > 0.01f;
        this.targetAngle = newTargetAngle;

        boolean isPowered = this.averageVoltage.get() >= 10.0;
        float prevSpeed = this.getSpeed();

        if (isPowered) {
            float diff = this.targetAngle - this.angle;
            if (Math.abs(diff) > 0.01f) {
                float step = 6.0f;
                float move = Math.signum(diff) * Math.min(Math.abs(diff), step);
                this.angle += move;

                if (this.movedContraption != null) {
                    this.applyRotation();
                }
                this.sendData();
            } else if (targetChanged) {
                this.sendData();
            }
        } else if (targetChanged) {
            this.sendData();
        }

        if (this.movedContraption == null) {
            float currentSpeed = this.getGeneratedSpeed();
            if (currentSpeed != prevSpeed) {
                this.updateGeneratedRotation();
            }
        }
    }

    protected void applyRotation() {
        if (this.movedContraption == null) {
            return;
        }
        this.movedContraption.setAngle(this.angle);
        BlockState blockState = this.getBlockState();
        if (blockState.hasProperty(ServoBlock.FACING)) {
            this.movedContraption.setRotationAxis(blockState.getValue(ServoBlock.FACING).getAxis());
        }
    }

    protected boolean isNearInitialAngle() {
        float modAngle = Math.abs(this.angle) % 360.0f;
        return modAngle < 22.5f || modAngle > 337.5f;
    }

    @Override
    public float getGeneratedSpeed() {
        if (this.movedContraption != null) return 0.0f;
        boolean isPowered = this.averageVoltage.get() >= 10.0;
        if (!isPowered) return 0.0f;
        float diff = this.targetAngle - this.angle;
        if (Math.abs(diff) <= 0.01f) return 0.0f;
        float baseSpeed = Math.signum(diff) * 32.0f;
        Direction facing = this.getBlockState().getValue(ServoBlock.FACING);
        return convertToDirection(baseSpeed, facing);
    }

    @Override
    public float calculateAddedStressCapacity() {
        if (this.movedContraption != null) return 0.0f;
        float speed = Math.abs(this.getGeneratedSpeed());
        if (speed == 0.0f) return 0.0f;
        return 1024.0f / speed;
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putBoolean("Running", this.running);
        compound.putFloat("Angle", this.angle);
        compound.putFloat("TargetAngle", this.targetAngle);
        this.averageVoltage.write(compound, "Voltage");
        AssemblyException.write(compound, registries, this.lastException);
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        if (this.wasMoved) {
            super.read(compound, registries, clientPacket);
            return;
        }
        float angleBefore = this.angle;
        this.running = compound.getBoolean("Running");
        this.angle = compound.getFloat("Angle");
        this.targetAngle = compound.getFloat("TargetAngle");
        this.averageVoltage.read(compound, "Voltage");
        this.lastException = AssemblyException.read(compound, registries);
        super.read(compound, registries, clientPacket);
        if (!clientPacket) return;

        if (this.running) {
            if (this.movedContraption == null || !this.movedContraption.isStalled()) {
                if (Math.abs(this.angle - angleBefore) > 12.0f) {
                    this.prevAngle = this.angle;
                } else {
                    this.angle = angleBefore;
                }
            }
        } else {
            this.movedContraption = null;
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("gui.goggles.generator_stats").forGoggles(tooltip);
        double voltage = this.averageVoltage.get();
        boolean powered = voltage >= 10.0;

        CEELang.builder().translate("gui.goggles.voltage").style(ChatFormatting.GRAY).forGoggles(tooltip);
        CreateLang.number(voltage).translate("generic.unit.voltage").style(ChatFormatting.AQUA).forGoggles(tooltip, 1);

        Component stateText = powered ? Component.translatable("gui.goggles.servo.powered").withStyle(ChatFormatting.GREEN)
                : Component.translatable("gui.goggles.servo.unpowered").withStyle(ChatFormatting.RED);
        tooltip.add(Component.translatable("gui.goggles.servo.status").withStyle(ChatFormatting.GRAY).append(": ").append(stateText));

        tooltip.add(Component.translatable("gui.goggles.servo.target_angle").withStyle(ChatFormatting.GRAY).append(": ")
                .append(Component.literal(Math.round(this.targetAngle) + "°").withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.translatable("gui.goggles.servo.current_angle").withStyle(ChatFormatting.GRAY).append(": ")
                .append(Component.literal(Math.round(this.angle) + "°").withStyle(ChatFormatting.AQUA)));

        if (this.running) {
            tooltip.add(Component.translatable("gui.goggles.servo.contraption_assembled").withStyle(ChatFormatting.GOLD));
        }
        return true;
    }
}
