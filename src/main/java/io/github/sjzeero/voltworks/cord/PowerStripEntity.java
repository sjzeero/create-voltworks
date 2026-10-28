package io.github.sjzeero.voltworks.cord;

import io.github.sjzeero.voltworks.reel.LooseEndPhysics;
import io.github.sjzeero.voltworks.registry.VoltworksEntities;
import io.github.sjzeero.voltworks.registry.VoltworksItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class PowerStripEntity extends Entity {
    private static final EntityDataAccessor<Optional<BlockPos>> SOCKET = SynchedEntityData.defineId(PowerStripEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    public static final double CARRY_DISTANCE = 1.3;
    public static final double CARRY_SLIP = 3.5;

    private @Nullable UUID carrier;
    private @Nullable BlockPos linked;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYaw;
    private final Supplier<StripLinks.Placement> link = () -> placement(StripLinks.partialTick);

    public PowerStripEntity(EntityType<? extends PowerStripEntity> type, Level level) {
        super(type, level);
    }

    public PowerStripEntity(Level level, Vec3 at, double yaw) {
        this(VoltworksEntities.POWER_STRIP.get(), level);
        setPos(at.x, at.y, at.z);
        setYaw(yaw);
        yRotO = getYRot();
    }

    public double yaw() {
        return Math.toRadians(getYRot());
    }

    public void setYaw(double yaw) {
        setYRot((float) Mth.wrapDegrees(Math.toDegrees(yaw)));
    }

    public double yaw(float pt) {
        return Math.toRadians(Mth.rotLerp(pt, yRotO, getYRot()));
    }

    public StripLinks.Placement placement(float pt) {
        return new StripLinks.Placement(getPosition(pt), yaw(pt));
    }

    public @Nullable BlockPos socket() {
        return entityData.get(SOCKET).orElse(null);
    }

    public void setSocket(@Nullable BlockPos socket) {
        entityData.set(SOCKET, Optional.ofNullable(socket));
    }

    public boolean carriedBy(Player p) {
        return p.getUUID().equals(carrier);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SOCKET, Optional.empty());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (!(level() instanceof ServerLevel sl))
            return;
        LooseCords cords = LooseCords.get(sl);
        Vec3 anchor = cords.stripTick(this);
        Vec3 before = position();
        if (!carry(sl, anchor))
            fall(anchor);
        Vec3 moved = position().subtract(before);
        if (anchor != null && moved.lengthSqr() > 1.0E-6)
            setYaw(StripGeometry.yawToward(anchor.x - getX(), anchor.z - getZ(), yaw()));
    }

    private boolean carry(ServerLevel sl, @Nullable Vec3 anchor) {
        if (carrier == null)
            return false;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(carrier);
        if (p == null || p.level() != sl || !p.isAlive()) {
            carrier = null;
            return false;
        }
        Vec3 look = p.getLookAngle();
        Vec3 target = p.getEyePosition().add(look.x * CARRY_DISTANCE, look.y * CARRY_DISTANCE - 0.35, look.z * CARRY_DISTANCE);
        if (anchor != null && target.distanceTo(anchor) > StripGeometry.CENTER_REACH)
            target = anchor.add(target.subtract(anchor).normalize().scale(StripGeometry.CENTER_REACH));
        if (target.distanceTo(p.getEyePosition()) > CARRY_SLIP) {
            carrier = null;
            p.displayClientMessage(Component.translatable("voltworks.power_strip.slipped").withStyle(ChatFormatting.RED), true);
            sl.playSound(null, p.blockPosition(), SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 0.8f, 1.2f);
            return false;
        }
        setDeltaMovement(Vec3.ZERO);
        setPos(target.x, target.y, target.z);
        if (anchor == null)
            setYaw(StripGeometry.yawToward(p.getX() - getX(), p.getZ() - getZ(), yaw()));
        return true;
    }

    private void fall(@Nullable Vec3 anchor) {
        BlockPos in = BlockPos.containing(getX(), getY() + 0.01, getZ());
        if (level().getBlockState(in).isCollisionShapeFullBlock(level(), in)
                && !level().getBlockState(in.above()).isCollisionShapeFullBlock(level(), in.above()))
            setPos(getX(), in.getY() + 1, getZ());
        Vector3d pos = new Vector3d(getX(), getY(), getZ());
        Vec3 dv = getDeltaMovement();
        Vector3d vel = new Vector3d(dv.x, dv.y, dv.z);
        LooseEndPhysics.Step step = anchor != null
                ? LooseEndPhysics.step(pos, vel, new Vector3d(anchor.x, anchor.y, anchor.z), StripGeometry.CENTER_REACH)
                : LooseEndPhysics.step(pos, vel, pos, Double.POSITIVE_INFINITY);
        setDeltaMovement(step.velocity().x, step.velocity().y, step.velocity().z);
        if (step.teleport())
            setPos(step.move().x, step.move().y, step.move().z);
        else
            move(MoverType.SELF, new Vec3(step.move().x, step.move().y, step.move().z));
        Vec3 after = getDeltaMovement();
        Vector3d f = LooseEndPhysics.friction(new Vector3d(after.x, after.y, after.z), onGround());
        setDeltaMovement(f.x, onGround() && f.y < 0 ? 0 : f.y, f.z);
    }

    private void clientTick() {
        if (lerpSteps > 0) {
            setPos(getX() + (lerpX - getX()) / lerpSteps, getY() + (lerpY - getY()) / lerpSteps, getZ() + (lerpZ - getZ()) / lerpSteps);
            setYRot(getYRot() + Mth.wrapDegrees(lerpYaw - getYRot()) / lerpSteps);
            lerpSteps--;
        }
        BlockPos socket = socket();
        if (linked != null && !linked.equals(socket)) {
            StripLinks.remove(level(), linked, link);
            linked = null;
        }
        if (socket != null && linked == null) {
            linked = socket;
            StripLinks.put(level(), socket, link);
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYaw = yRot;
        lerpSteps = Math.max(1, steps);
    }

    @Override
    public void onClientRemoval() {
        if (linked != null)
            StripLinks.remove(level(), linked, link);
        linked = null;
        super.onClientRemoval();
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND)
            return InteractionResult.PASS;
        ItemStack held = player.getMainHandItem();
        boolean cutters = LooseCords.isCutters(held);
        boolean cord = LooseCords.isLooseCord(held);
        if (!held.isEmpty() && !cutters && !cord)
            return InteractionResult.PASS;
        if (!(level() instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp))
            return InteractionResult.SUCCESS;
        LooseCords cords = LooseCords.get(sl);
        Vector3d px = StripGeometry.unturn(yaw(), new Vector3d(hit.x, hit.y, hit.z));
        int outlet = StripGeometry.outletAt(px);
        if (cord) {
            if (cords.plugHeldStrip(sp, held, this, outlet < 0 ? nearest(px) : outlet))
                held.setCount(0);
            return InteractionResult.SUCCESS;
        }
        if (cutters) {
            if (!cords.useStripOutlet(sp, this, outlet, true))
                cords.stowStrip(sp, this);
            return InteractionResult.SUCCESS;
        }
        if (carriedBy(sp)) {
            carrier = null;
            sl.playSound(null, blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.PLAYERS, 0.6f, 1.2f);
            return InteractionResult.SUCCESS;
        }
        if (sp.isShiftKeyDown()) {
            cords.pickUpStrip(sp, this);
            return InteractionResult.SUCCESS;
        }
        if (cords.useStripOutlet(sp, this, outlet, false) || cords.pullPlug(sp, this))
            return InteractionResult.SUCCESS;
        carrier = sp.getUUID();
        sl.playSound(null, blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS, 0.6f, 1.2f);
        sp.displayClientMessage(Component.translatable("voltworks.power_strip.carried"), true);
        return InteractionResult.SUCCESS;
    }

    private static int nearest(Vector3d px) {
        int best = StripGeometry.OUTLETS[0];
        for (int supply : StripGeometry.OUTLETS)
            if (Math.abs(px.x - StripGeometry.centerX(supply)) < Math.abs(px.x - StripGeometry.centerX(best)))
                best = supply;
        return best;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(VoltworksItems.POWER_STRIP_ITEM.get());
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel sl)
            LooseCords.get(sl).stripGone(this, reason == RemovalReason.KILLED);
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Socket"))
            setSocket(BlockPos.of(tag.getLong("Socket")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        BlockPos socket = socket();
        if (socket != null)
            tag.putLong("Socket", socket.asLong());
    }
}
