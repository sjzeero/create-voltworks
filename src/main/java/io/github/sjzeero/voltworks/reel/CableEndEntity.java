package io.github.sjzeero.voltworks.reel;

import io.github.sjzeero.voltworks.cord.LooseCords;
import io.github.sjzeero.voltworks.registry.VoltworksEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
import org.joml.Vector3d;

import java.util.UUID;

public class CableEndEntity extends Entity {
    private static final EntityDataAccessor<BlockPos> REEL = SynchedEntityData.defineId(CableEndEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<String> CORD = SynchedEntityData.defineId(CableEndEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> END = SynchedEntityData.defineId(CableEndEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> REACH = SynchedEntityData.defineId(CableEndEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<BlockPos> OTHER_POS = SynchedEntityData.defineId(CableEndEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> OTHER_SUPPLY = SynchedEntityData.defineId(CableEndEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OTHER_PLAYER = SynchedEntityData.defineId(CableEndEntity.class, EntityDataSerializers.INT);
    private @Nullable UUID cordId;
    private @Nullable CableEndEntity otherCache;

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

    public CableEndEntity(Level level, UUID cord, int end, float reach, Vec3 pos, Vec3 motion) {
        this(VoltworksEntities.CABLE_END.get(), level);
        cordId = cord;
        entityData.set(CORD, cord.toString());
        entityData.set(END, end);
        entityData.set(REACH, reach);
        setPos(pos.x, pos.y, pos.z);
        setDeltaMovement(motion);
        setYRot(level.random.nextFloat() * 360f);
    }

    public boolean isCord() {
        return !entityData.get(CORD).isEmpty();
    }

    public @Nullable UUID cordId() {
        if (cordId == null && isCord()) {
            try {
                cordId = UUID.fromString(entityData.get(CORD));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return cordId;
    }

    public int end() {
        return entityData.get(END);
    }

    public float reach() {
        return entityData.get(REACH);
    }

    public void setOther(BlockPos pos, int supply, int player, float reach) {
        entityData.set(OTHER_POS, pos);
        entityData.set(OTHER_SUPPLY, supply);
        entityData.set(OTHER_PLAYER, player);
        entityData.set(REACH, reach);
    }

    public BlockPos otherPos() {
        return entityData.get(OTHER_POS);
    }

    public int otherSupply() {
        return entityData.get(OTHER_SUPPLY);
    }

    public @Nullable Player otherHolder() {
        int id = entityData.get(OTHER_PLAYER);
        return id >= 0 && level().getEntity(id) instanceof Player p ? p : null;
    }

    public @Nullable CableEndEntity otherEnd() {
        if (otherCache != null && !otherCache.isRemoved())
            return otherCache;
        otherCache = null;
        UUID id = cordId();
        if (id == null)
            return null;
        double r = reach() + 8;
        for (CableEndEntity e : level().getEntitiesOfClass(CableEndEntity.class, getBoundingBox().inflate(r), e -> e != this && id.equals(e.cordId()) && e.end() != end()))
            return otherCache = e;
        return null;
    }

    public @Nullable Vec3 otherAt(float pt) {
        int supply = otherSupply();
        if (supply >= 0)
            return level().isLoaded(otherPos()) ? LooseCords.plugWorld(level(), otherPos(), supply) : null;
        Player p = otherHolder();
        if (p != null)
            return p.getPosition(pt).add(0, p.getBbHeight() * 0.55, 0);
        CableEndEntity o = otherEnd();
        return o == null ? null : o.getPosition(pt);
    }

    public BlockPos reelPos() {
        return entityData.get(REEL);
    }

    public @Nullable CableReelBlockEntity reel() {
        BlockPos pos = reelPos();
        if (!level().isLoaded(pos) || !(level().getBlockEntity(pos) instanceof CableReelBlockEntity be))
            return null;
        return level().isClientSide || be.isLooseEnd(getUUID()) ? be : null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(REEL, BlockPos.ZERO);
        builder.define(CORD, "");
        builder.define(END, 0);
        builder.define(REACH, 1f);
        builder.define(OTHER_POS, BlockPos.ZERO);
        builder.define(OTHER_SUPPLY, -1);
        builder.define(OTHER_PLAYER, -1);
    }

    @Override
    public void tick() {
        super.tick();
        BlockPos in = BlockPos.containing(getX(), getY() + 0.01, getZ());
        if (level().getBlockState(in).isCollisionShapeFullBlock(level(), in)
                && !level().getBlockState(in.above()).isCollisionShapeFullBlock(level(), in.above()))
            setPos(getX(), in.getY() + 1, getZ());

        Vector3d pos = new Vector3d(getX(), getY(), getZ());
        Vec3 dv = getDeltaMovement();
        Vector3d vel = new Vector3d(dv.x, dv.y, dv.z);
        CableReelBlockEntity be = isCord() ? null : level().isLoaded(reelPos()) ? reel() : null;
        Vec3 anchor = isCord() ? otherAt(1) : null;
        LooseEndPhysics.Step step;
        if (anchor != null) {
            step = LooseEndPhysics.step(pos, vel, new Vector3d(anchor.x, anchor.y, anchor.z), reach());
        } else if (be != null) {
            Vec3 o = be.outletWorld();
            step = LooseEndPhysics.step(pos, vel, new Vector3d(o.x, o.y, o.z), be.stored());
        } else {
            step = LooseEndPhysics.step(pos, vel, pos, Double.POSITIVE_INFINITY);
        }
        setDeltaMovement(step.velocity().x, step.velocity().y, step.velocity().z);
        if (step.teleport())
            setPos(step.move().x, step.move().y, step.move().z);
        else
            move(MoverType.SELF, new Vec3(step.move().x, step.move().y, step.move().z));
        Vec3 after = getDeltaMovement();
        Vector3d f = LooseEndPhysics.friction(new Vector3d(after.x, after.y, after.z), onGround());
        setDeltaMovement(f.x, onGround() && f.y < 0 ? 0 : f.y, f.z);
        if (!level().isClientSide)
            serverTick();
    }

    private void serverTick() {
        if (isCord()) {
            if (level() instanceof ServerLevel sl && !LooseCords.get(sl).entityTick(this))
                discard();
            return;
        }
        BlockPos pos = reelPos();
        if (!level().isLoaded(pos))
            return;
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
        if (isCord()) {
            if (player instanceof ServerPlayer sp && level() instanceof ServerLevel sl) {
                if (LooseCords.isCutters(player.getMainHandItem()))
                    LooseCords.get(sl).cutEntity(sp, this);
                else
                    LooseCords.get(sl).pickUp(sp, this);
            }
            return InteractionResult.SUCCESS;
        }
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

    @Override
    public void remove(RemovalReason reason) {
        if (isCord() && reason.shouldDestroy() && level() instanceof ServerLevel sl) {
            LooseCords.get(sl).entityGone(this);
            super.remove(reason);
            return;
        }
        if (!level().isClientSide && reason.shouldDestroy() && level().isLoaded(reelPos())
                && level().getBlockEntity(reelPos()) instanceof CableReelBlockEntity be)
            be.looseGone(getUUID());
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(REEL, BlockPos.of(tag.getLong("Reel")));
        if (tag.hasUUID("Cord")) {
            cordId = tag.getUUID("Cord");
            entityData.set(CORD, cordId.toString());
            entityData.set(END, tag.getInt("End"));
            entityData.set(REACH, tag.getFloat("Reach"));
            entityData.set(OTHER_POS, BlockPos.of(tag.getLong("OtherPos")));
            entityData.set(OTHER_SUPPLY, tag.contains("OtherSupply") ? tag.getInt("OtherSupply") : -1);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Reel", reelPos().asLong());
        UUID id = cordId();
        if (id != null) {
            tag.putUUID("Cord", id);
            tag.putInt("End", end());
            tag.putFloat("Reach", reach());
            tag.putLong("OtherPos", otherPos().asLong());
            tag.putInt("OtherSupply", otherSupply());
        }
    }
}
