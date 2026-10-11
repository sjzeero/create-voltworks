package io.github.sjzeero.voltworks.solar;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.contraptions.bearing.IBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.ServerSpeedProvider;
import io.github.sjzeero.voltworks.Voltworks;
import java.util.List;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import io.github.sjzeero.voltworks.registry.VoltworksBlockEntities;

/**
 * Ported from Create: Power Grid (patryk3211, Apache-2.0) to Voltworks by sjzeero; electrical side reimplemented on CEE devices.
 * A bearing that rotates attached solar panels and sums their output on its two terminals.
 */
public class SolarPanelBearingBlockEntity extends KineticBlockEntity implements IBearingBlockEntity, IDisplayAssemblyExceptions {
    protected ControlledContraptionEntity movedContraption;
    protected float angle;
    protected boolean running;
    protected boolean assembleNextTick;
    protected float clientAngleDiff;
    protected AssemblyException lastException;
    protected double sequencedAngleLimit = -1;
    protected SolarPanelBearingScrollBehaviour parallelNumbers;
    private float prevAngle;
    private int rayDelay;
    private float sunVisibility;
    private boolean skyVisible;

    /** Read by {@link SolarPanelBearingDevice}. */
    public volatile double irradiance, ambient = 22, lastVoltage;
    public volatile int series = 1, parallel = 1;

    public SolarPanelBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(3);
    }

    public SolarPanelBearingBlockEntity(BlockPos pos, BlockState state) {
        this(VoltworksBlockEntities.SOLAR_PANEL_BEARING_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        parallelNumbers = new SolarPanelBearingScrollBehaviour(this);
        behaviours.add(parallelNumbers);
    }

    public boolean isRunning() {
        return running;
    }

    public void toggleAssembly() {
        if (running) disassemble();
        else assembleNextTick = true;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (movedContraption != null && !level.isClientSide) sendData();
    }

    @Override
    public void tick() {
        super.tick();
        prevAngle = angle;
        if (level.isClientSide) clientAngleDiff /= 2;

        if (!level.isClientSide && assembleNextTick) {
            assembleNextTick = false;
            if (running) {
                if (speed == 0 && (movedContraption == null || movedContraption.getContraption().getBlocks().isEmpty())) {
                    if (movedContraption != null) movedContraption.getContraption().stop(level);
                    disassemble();
                    return;
                }
            } else assemble();
        }

        if (running) {
            if (movedContraption == null || !movedContraption.isStalled()) {
                float s = getAngularSpeed();
                if (sequencedAngleLimit >= 0) {
                    s = (float) Mth.clamp(s, -sequencedAngleLimit, sequencedAngleLimit);
                    sequencedAngleLimit = Math.max(0, sequencedAngleLimit - Math.abs(s));
                }
                angle = (angle + s) % 360;
            }
            applyRotation();
        }
        if (!level.isClientSide) solarTick();
    }

    private void solarTick() {
        if (!running || movedContraption == null || !(movedContraption.getContraption() instanceof SolarPanelBearingContraption c)
                || c.getPanelNormal() == null || c.getPanelBlocks() < 1) {
            irradiance = 0;
            return;
        }
        Vec3 local = c.getPanelNormal();
        Vec3 normal = movedContraption.toGlobalVector(local, 1).subtract(movedContraption.toGlobalVector(Vec3.ZERO, 1)).normalize();
        Vec3 center = contraptionCenter();
        BlockPos cpos = BlockPos.containing(center);
        ambient = SolarPhysics.ambientTemp(level, cpos);
        parallel = parallelNumbers.getDivisor();
        series = Math.max(1, c.getPanelBlocks() / parallel);
        irradiance = computeIrradiance(center, cpos, normal);
    }

    private double computeIrradiance(Vec3 center, BlockPos cpos, Vec3 normal) {
        double am = SolarPhysics.airMass(level);
        if (am == Double.POSITIVE_INFINITY) return 0;
        double cloud = SolarPhysics.cloudCover(level);
        double trans = 1 - cloud;
        double irr = SolarPhysics.SOLAR_CONSTANT * Math.pow(0.7, Math.pow(am, 0.678));
        irr = Math.min(SolarPhysics.SOLAR_CONSTANT, irr * (((cpos.getY() - 70) / 250f) * 0.04f + 1));
        double a = level.getSunAngle(0);
        Vec3 sun = new Vec3(-Math.sin(a), Math.cos(a), 0);
        if (sun.y <= 0) return 0;
        if (rayDelay-- <= 0) {
            Vec3 start = center.add(normal);
            sunVisibility = SolarPhysics.trace(level, start, start.add(sun.scale(160)), BlockPos.containing(start));
            skyVisible = SolarPhysics.skyVisible(level, BlockPos.containing(start));
            rayDelay = level.random.nextInt(41) + 10;
        }
        double cos = Math.max(0, sun.dot(normal));
        double diffuse = 0, reflected = 0;
        if (skyVisible) {
            diffuse = SolarPhysics.DIFFUSE_FRAC * sun.y * irr * trans * (1 + cloud) * ((1 + normal.y) / 2.0);
            reflected = SolarPhysics.ALBEDO_FRAC * sun.y * irr * trans * ((1 - normal.y) / 2.0);
        }
        return irr * sunVisibility * trans * cos + diffuse + reflected;
    }

    private Vec3 contraptionCenter() {
        double x = 0, y = 0, z = 0;
        int n = 0;
        for (BlockPos p : movedContraption.getContraption().getBlocks().keySet()) {
            x += p.getX() + .5; y += p.getY() + .5; z += p.getZ() + .5; n++;
        }
        if (n == 0) return movedContraption.position();
        return movedContraption.toGlobalVector(new Vec3(x / n, y / n, z / n), 1);
    }

    public void assemble() {
        if (!(level.getBlockState(worldPosition).getBlock() instanceof SolarPanelBearingBlock)) return;
        Direction direction = getBlockState().getValue(SolarPanelBearingBlock.FACING);
        SolarPanelBearingContraption contraption = new SolarPanelBearingContraption(direction);
        try {
            if (!contraption.assemble(level, worldPosition)) return;
            lastException = null;
        } catch (AssemblyException e) {
            lastException = e;
            sendData();
            return;
        }
        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
        movedContraption = ControlledContraptionEntity.create(level, this, contraption);
        BlockPos anchor = worldPosition.relative(direction);
        movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        movedContraption.setRotationAxis(direction.getAxis());
        level.addFreshEntity(movedContraption);
        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, worldPosition);
        running = true;
        angle = 0;
        parallelNumbers.refreshDivisors(contraption.getPanelBlocks());
        rayDelay = 0;
        sendData();
    }

    public void disassemble() {
        if (!running && movedContraption == null) return;
        angle = 0;
        sequencedAngleLimit = -1;
        if (movedContraption != null) {
            movedContraption.disassemble();
            AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(level, worldPosition);
        }
        movedContraption = null;
        running = false;
        assembleNextTick = false;
        irradiance = 0;
        sendData();
    }

    @Override
    public AssemblyException getLastAssemblyException() {
        return lastException;
    }

    @Override
    public float getInterpolatedAngle(float partialTicks) {
        if (isVirtual()) return Mth.lerp(partialTicks + .5f, prevAngle, angle);
        if (movedContraption == null || movedContraption.isStalled() || !running) partialTicks = 0;
        float s = getAngularSpeed();
        if (sequencedAngleLimit >= 0) s = (float) Mth.clamp(s, -sequencedAngleLimit, sequencedAngleLimit);
        return Mth.lerp(partialTicks, angle, angle + s);
    }

    public float getAngularSpeed() {
        if (getSpeed() == 0) return 0;
        float s = convertToAngular(getSpeed());
        if (level.isClientSide) {
            s *= ServerSpeedProvider.get();
            s += clientAngleDiff / 3;
        }
        return s;
    }

    @Override
    public void onSpeedChanged(float prevSpeed) {
        super.onSpeedChanged(prevSpeed);
        sequencedAngleLimit = -1;
        if (movedContraption != null && Math.signum(prevSpeed) != Math.signum(getSpeed()) && prevSpeed != 0) {
            if (!movedContraption.isStalled()) {
                angle = Math.round(angle);
                applyRotation();
            }
            movedContraption.getContraption().stop(level);
        }
        if (sequenceContext != null && sequenceContext.instruction() == SequencerInstructions.TURN_ANGLE)
            sequencedAngleLimit = sequenceContext.getEffectiveValue(getTheoreticalSpeed());
    }

    protected void applyRotation() {
        if (movedContraption == null) return;
        movedContraption.setAngle(angle);
        movedContraption.setRotationAxis(getBlockState().getValue(SolarPanelBearingBlock.FACING).getAxis());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putBoolean("Running", running);
        tag.putFloat("Angle", angle);
        if (sequencedAngleLimit >= 0) tag.putDouble("SequencedAngleLimit", sequencedAngleLimit);
        tag.putInt("StringsInParallel", parallelNumbers.getDivisor());
        tag.putInt("PanelCount", parallelNumbers.getPanelCount());
        tag.putDouble("Irradiance", irradiance);
        tag.putDouble("LastVoltage", lastVoltage);
        AssemblyException.write(tag, registries, lastException);
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        if (wasMoved) {
            super.read(tag, registries, clientPacket);
            return;
        }
        float angleBefore = angle;
        running = tag.getBoolean("Running");
        angle = tag.getFloat("Angle");
        sequencedAngleLimit = tag.contains("SequencedAngleLimit") ? tag.getDouble("SequencedAngleLimit") : -1;
        lastException = AssemblyException.read(tag, registries);
        if (tag.contains("PanelCount")) parallelNumbers.refreshDivisors(tag.getInt("PanelCount"));
        irradiance = tag.getDouble("Irradiance");
        lastVoltage = tag.getDouble("LastVoltage");
        super.read(tag, registries, clientPacket);
        if (tag.contains("StringsInParallel")) parallelNumbers.setByDivisor(tag.getInt("StringsInParallel"));
        if (!clientPacket) return;
        if (running) {
            if (movedContraption == null || !movedContraption.isStalled()) {
                clientAngleDiff = AngleHelper.getShortestAngleDiff(angleBefore, angle);
                angle = angleBefore;
            }
        } else movedContraption = null;
    }

    @Override
    public void attach(ControlledContraptionEntity contraption) {
        if (!(contraption.getContraption() instanceof SolarPanelBearingContraption)) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(SolarPanelBearingBlock.FACING)) return;
        movedContraption = contraption;
        setChanged();
        BlockPos anchor = worldPosition.relative(state.getValue(SolarPanelBearingBlock.FACING));
        movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        if (!level.isClientSide) {
            running = true;
            sendData();
        }
    }

    @Override
    public boolean isWoodenTop() {
        return false;
    }

    @Override
    public void setAngle(float forcedAngle) {
        angle = forcedAngle;
    }

    @Override
    public boolean isAttachedTo(AbstractContraptionEntity contraption) {
        return movedContraption == contraption;
    }

    @Override
    public void onStall() {
        if (!level.isClientSide) sendData();
    }

    @Override
    public void remove() {
        if (!level.isClientSide) disassemble();
        super.remove();
    }

    @Override
    public boolean isValid() {
        return !isRemoved();
    }

    @Override
    public BlockPos getBlockPosition() {
        return worldPosition;
    }

    @Override
    protected boolean syncSequenceContext() {
        return true;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.literal("    ").append(Component.translatable("gui.goggles.voltworks.solar.irradiance").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("    ").append(Component.literal(String.format("%.0f W/m²", irradiance)).withStyle(ChatFormatting.YELLOW)));
        tooltip.add(Component.literal("    ").append(Component.translatable("gui.goggles.voltworks.motor.voltage").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("    ").append(Component.literal(String.format("%.1f V  (%d × %d)", lastVoltage, parallel, series)).withStyle(ChatFormatting.AQUA)));
        return true;
    }
}
