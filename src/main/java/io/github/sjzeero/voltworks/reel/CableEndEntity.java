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
 * the drum (it gets dragged back towards the reel instead), and a right-click picks it up as the Cable End item
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

    @Override
    public void tick() {
        super.tick();
        Vec3 dm = getDeltaMovement().add(0, -0.04, 0);
        move(MoverType.SELF, dm);
        double friction = onGround() ? 0.5 : 0.98;
        dm = getDeltaMovement().multiply(friction, 0.98, friction);
        if (onGround() && dm.y < 0)
            dm = new Vec3(dm.x, 0, dm.z);
        setDeltaMovement(dm);
        if (!level().isClientSide)
            serverTick();
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
        // the wire on the drum is all there is: past it the end is dragged back towards the reel
        Vec3 outlet = be.outletWorld();
        Vec3 here = position();
        double scale = ReelMath.reachScale(here.distanceTo(outlet), be.stored());
        if (scale < 1) {
            Vec3 target = outlet.add(here.subtract(outlet).scale(scale));
            move(MoverType.SELF, target.subtract(here));
            if (position().distanceTo(outlet) > be.stored() + 0.5)
                setPos(target.x, target.y, target.z); // stuck behind a block: the cable pulls it through
            setDeltaMovement(getDeltaMovement().scale(0.5));
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
