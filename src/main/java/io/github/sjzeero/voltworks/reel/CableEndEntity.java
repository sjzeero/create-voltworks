package io.github.sjzeero.voltworks.reel;

import io.github.sjzeero.voltworks.registry.VoltworksEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The free end of a reel cable lying on the ground: dropped, thrown, slipped out of the hand, or left behind when the
 * block it was plugged into went away. It falls like an item, can't be pushed further from its reel than the wire on
 * the drum (past it the wire holds it like a rope: it swings and hangs below the reel, never floats), and a right-click picks it up as the Cable End item
 * again. It carries no current: there is no Electro Energetics connection while the end lies around.
 * <p>
 * The reel remembers the entity's UUID; the entity only remembers its reel. Either side going away clears the link
 * (the reel winds back, the entity disappears).
 */
public class CableEndEntity extends Entity {
    private static final EntityDataAccessor<BlockPos> REEL = SynchedEntityData.defineId(CableEndEntity.class, EntityDataSerializers.BLOCK_POS);

    public CableEndEntity(EntityType<? extends CableEndEntity> type, Level level) {
        super(type, level);
    }

    public CableEndEntity(Level level, BlockPos reel, Vec3 pos, Vec3 motion) {
        this(VoltworksEntities.CABLE_END.get(), level);
        entityData.set(REEL, reel);
        setPos(pos.x, pos.y, pos.z);
        setDeltaMovement(motion);
        setYRot(level.random.nextFloat() * 360f);
    }

    public BlockPos reelPos() {
        return entityData.get(REEL);
    }

    /** The reel this end belongs to, if loaded (on the server: only while the reel still points at this entity). */
    public @Nullable CableReelBlockEntity reel() {
        BlockPos pos = reelPos();
        if (!level().isLoaded(pos) || !(level().getBlockEntity(pos) instanceof CableReelBlockEntity be))
            return null;
        return level().isClientSide || be.isLooseEnd(getUUID()) ? be : null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(REEL, BlockPos.ZERO);
    }

    /** Gravity per tick, like a dropped item. */
    static final double GRAVITY = 0.04;

    @Override
    public void tick() {
        super.tick();
        // spawned inside a block (dropped against a wall, a socket that went away): push out like an item does
        noPhysics = !level().noCollision(this, getBoundingBox().deflate(1.0E-7));
        if (noPhysics)
            moveTowardsClosestSpace(getX(), (getBoundingBox().minY + getBoundingBox().maxY) / 2.0, getZ());
        // gravity every tick, no matter what: the end never rests anywhere but on something solid or on a taut wire
        Vec3 dm = getDeltaMovement().add(0, -GRAVITY, 0);
        dm = constrain(dm); // on the client too (the reel's stored length is synced), so a hanging end doesn't jitter
        move(MoverType.SELF, dm);
        double friction = onGround() ? 0.5 : 0.98;
        setDeltaMovement(getDeltaMovement().multiply(friction, 0.98, friction));
        if (!level().isClientSide)
            serverTick();
    }

    /**
     * The wire on the drum is a rope, not a rod: while the end is within reach nothing happens; when this tick's
     * motion would carry it past the reach, the outward part of the motion is taken away (it swings and slides down
     * along the sphere around the outlet instead of floating), and an end already beyond the reach is pulled back.
     */
    private Vec3 constrain(Vec3 dm) {
        CableReelBlockEntity be = level().isLoaded(reelPos()) ? reel() : null;
        if (be == null)
            return dm;
        Vec3 outlet = be.outletWorld();
        double reach = be.stored();
        Vec3 next = position().add(dm);
        Vec3 off = next.subtract(outlet);
        double dist = off.length();
        if (dist <= reach + ReelMath.EPS || dist < 1.0E-6)
            return dm;
        Vec3 n = off.scale(1 / dist);
        Vec3 onSphere = outlet.add(n.scale(reach));
        Vec3 step = onSphere.subtract(position());
        // keep the tangential velocity (the swing), drop the outward one
        Vec3 v = getDeltaMovement();
        double out = v.dot(n);
        if (out > 0)
            setDeltaMovement(v.subtract(n.scale(out)));
        hasImpulse = true;
        if (position().distanceTo(outlet) > reach + 2) {
            // stuck behind a block far past the reach: the cable pulls it through
            setPos(onSphere.x, onSphere.y, onSphere.z);
            return Vec3.ZERO;
        }
        return step;
    }

    private void serverTick() {
        BlockPos pos = reelPos();
        if (!level().isLoaded(pos))
            return; // reel not loaded: just lie here, it checks the link again once it is
        CableReelBlockEntity be = reel();
        if (be == null) {
            discard();
            return;
        }
        if (tickCount % 20 == 0)
            be.looseSeen(blockPosition());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND)
            return InteractionResult.PASS;
        if (level().isClientSide)
            return InteractionResult.SUCCESS;
        CableReelBlockEntity be = reel();
        if (be == null)
            discard();
        else if (player instanceof ServerPlayer sp)
            be.pickUpLoose(sp, this);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Killed, fell out of the world, /kill...: the reel winds the cable back in. Unloading keeps the link. */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy() && level().isLoaded(reelPos())
                && level().getBlockEntity(reelPos()) instanceof CableReelBlockEntity be)
            be.looseGone(getUUID());
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(REEL, BlockPos.of(tag.getLong("Reel")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Reel", reelPos().asLong());
    }
}
