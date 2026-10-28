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
import java.util.function.Supplier;

public class PowerStripEntity extends Entity {
    private static final EntityDataAccessor<Optional<BlockPos>> SOCKET = SynchedEntityData.defineId(PowerStripEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Integer> CARRIER = SynchedEntityData.defineId(PowerStripEntity.class, EntityDataSerializers.INT);

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
        Player p = carrier();
        if (p != null && level().isClientSide) {
            Vec3 at = held(p, pt);
            return new StripLinks.Placement(at, heldYaw(p, at, pt));
        }
        return new StripLinks.Placement(getPosition(pt), yaw(pt));
    }

    public @Nullable Player carrier() {
        int id = entityData.get(CARRIER);
        return id >= 0 && level().getEntity(id) instanceof Player p && p.isAlive() ? p : null;
    }

    private void setCarrier(@Nullable Player p) {
        entityData.set(CARRIER, p == null ? -1 : p.getId());
    }

    private @Nullable Vec3 socketAnchor() {
        BlockPos socket = socket();
        return socket != null && level().isLoaded(socket) ? LooseCords.plugWorld(level(), socket, SocketBlock.CORD_POSITIVE) : null;
    }

    private Vec3 held(Player p, float pt) {
        Vec3 eye = p.getEyePosition(pt), look = p.getViewVector(pt);
        Vector3d target = StripGeometry.holdTarget(new Vector3d(eye.x, eye.y, eye.z), new Vector3d(look.x, look.y, look.z));
        Vec3 anchor = socketAnchor();
        if (anchor != null)
            target = StripGeometry.clampToCord(target, new Vector3d(anchor.x, anchor.y, anchor.z));
        return new Vec3(target.x, target.y, target.z);
    }

    private double heldYaw(Player p, Vec3 at, float pt) {
        Vec3 anchor = socketAnchor();
        Vec3 toward = anchor != null ? anchor : p.getPosition(pt);
        return StripGeometry.yawToward(toward.x - at.x, toward.z - at.z, yaw(pt));
    }

    public @Nullable BlockPos socket() {
        return entityData.get(SOCKET).orElse(null);
    }

    public void setSocket(@Nullable BlockPos socket) {
        entityData.set(SOCKET, Optional.ofNullable(socket));
    }

    public boolean carriedBy(Player p) {
        return entityData.get(CARRIER) == p.getId();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SOCKET, Optional.empty());
        builder.define(CARRIER, -1);
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
        if (carry(sl))
            return;
        Vec3 before = position();
        fall(anchor);
        Vec3 moved = position().subtract(before);
        if (anchor != null && moved.lengthSqr() > 1.0E-6)
            setYaw(StripGeometry.yawToward(anchor.x - getX(), anchor.z - getZ(), yaw()));
    }

    private boolean carry(ServerLevel sl) {
        if (entityData.get(CARRIER) < 0)
            return false;
        Player p = carrier();
        if (!(p instanceof ServerPlayer sp) || p.level() != sl) {
            setCarrier(null);
            return false;
        }
        Vec3 at = held(sp, 1);
        Vec3 eye = sp.getEyePosition();
        if (StripGeometry.slips(new Vector3d(eye.x, eye.y, eye.z), new Vector3d(at.x, at.y, at.z))) {
            setCarrier(null);
            sp.displayClientMessage(Component.translatable("voltworks.power_strip.slipped").withStyle(ChatFormatting.RED), true);
            sl.playSound(null, sp.blockPosition(), SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 0.8f, 1.2f);
            return false;
        }
        setDeltaMovement(Vec3.ZERO);
        setPos(at.x, at.y, at.z);
        setYaw(heldYaw(sp, at, 1));
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
        Player p = carrier();
        if (p != null) {
            Vec3 at = held(p, 1);
            setPos(at.x, at.y, at.z);
            setYaw(heldYaw(p, at, 1));
            lerpSteps = 0;
        } else if (lerpSteps > 0) {
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
            setCarrier(null);
            sl.playSound(null, blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.PLAYERS, 0.6f, 1.2f);
            return InteractionResult.SUCCESS;
        }
        if (sp.isShiftKeyDown()) {
            cords.pickUpStrip(sp, this);
            return InteractionResult.SUCCESS;
        }
        if (cords.useStripOutlet(sp, this, outlet, false) || cords.pullPlug(sp, this))
            return InteractionResult.SUCCESS;
        setCarrier(sp);
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
