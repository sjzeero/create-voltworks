package io.github.sjzeero.voltworks.reel;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.george_vi.electroenergetics.simulation.WireType;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import io.github.sjzeero.voltworks.VoltworksConfig;
import io.github.sjzeero.voltworks.cord.CordPair;
import io.github.sjzeero.voltworks.mixin.WireSpoolItemAccessor;
import io.github.sjzeero.voltworks.registry.VoltworksBlockEntities;
import io.github.sjzeero.voltworks.registry.VoltworksWireTypes;
import io.github.sjzeero.voltworks.util.Spools;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Wire bookkeeping of a cable reel. {@link #stored} is the length still wound on the drum, {@link #pulled} the length
 * of the connection that is out right now (zero when none), both in blocks. Their sum only changes by loading spools
 * or breaking the reel, so wire is never created or lost by pulling and winding back.
 */
public class CableReelBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private @Nullable ResourceLocation wireTypeId;
    private double stored;
    private double pulled;
    private @Nullable InWorldNode target;
    /** Id of the cable end currently in a player's hand; 0 when nobody holds it. */
    private long grab;

    public CableReelBlockEntity(BlockPos pos, BlockState state) {
        super(VoltworksBlockEntities.CABLE_REEL_BE.get(), pos, state);
    }

    public CableReelBlock block() {
        return (CableReelBlock) getBlockState().getBlock();
    }

    public double stored() {
        return stored;
    }

    public double pulled() {
        return pulled;
    }

    public long grabId() {
        return grab;
    }

    public void dropGrab() {
        if (grab != 0) {
            grab = 0;
            setChanged();
        }
    }

    public @Nullable WireType wireType() {
        if (block().cord)
            return VoltworksWireTypes.COPPER_CORD.get();
        return wireTypeId == null ? null : CEERegistries.WIRE_TYPE.get(wireTypeId);
    }

    private InWorldNode outlet(int offset) {
        return new InWorldNode(block().outletId() + offset, worldPosition);
    }

    /** Where the cable leaves the drum, in world space. */
    public Vec3 outletWorld() {
        Vec3 local = block().localNode(getBlockState(), block().outletId());
        return WallSpace.toWorld(level, Vec3.atLowerCornerOf(worldPosition).add(local));
    }

    // ---- loading ---------------------------------------------------------------------------------------------------

    public void load(ServerPlayer player, ItemStack held) {
        WireType type = ((WireSpoolItemAccessor) held.getItem()).voltworks$wireType().get();
        CableReelBlock b = block();
        if (b.cord ? !CordPair.isCord(type) : CordPair.isCord(type) || type.isDecorative()) {
            deny(player, b.cord ? "cord_only" : "wrong_wire");
            return;
        }
        WireType current = wireType();
        boolean empty = stored + pulled <= ReelMath.EPS;
        if (!b.cord && current != null && current != type && !empty) {
            deny(player, "other_wire", current.getSpooledItem().getDescription());
            return;
        }
        double spool = VoltworksConfig.reelSpoolLength(), cap = VoltworksConfig.reelCapacity();
        if (!ReelMath.canLoad(stored, pulled, spool, cap)) {
            deny(player, "full", fmt(stored + pulled), fmt(cap));
            return;
        }
        if (!b.cord)
            wireTypeId = CEERegistries.WIRE_TYPE.getKey(type);
        stored += spool;
        Spools.consume(player, held);
        sync();
        level.playSound(null, worldPosition, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.BLOCKS, 0.8f, 0.8f);
        AllSoundEvents.WRENCH_ROTATE.playOnServer(level, worldPosition);
        player.displayClientMessage(Component.translatable("voltworks.cable_reel.loaded", fmt(stored + pulled), fmt(cap)), true);
    }

    // ---- pulling out -----------------------------------------------------------------------------------------------

    public void grab(ServerPlayer player) {
        checkOut();
        if (pulled > 0) {
            deny(player, "already_out");
            return;
        }
        if (stored < 0.5 || wireType() == null) {
            deny(player, "empty");
            return;
        }
        long id;
        do id = level.random.nextLong(); while (id == 0);
        grab = id;
        setChanged();
        player.setItemInHand(InteractionHand.MAIN_HAND, ReelPlugItem.create(worldPosition, id));
        level.playSound(null, worldPosition, SoundEvents.FISHING_BOBBER_THROW, SoundSource.BLOCKS, 0.7f, 0.9f);
        player.displayClientMessage(Component.translatable("voltworks.cable_reel.grabbed", fmt(stored)), true);
    }

    /** Plugs the held cable end into the block at {@code pos}. True when the connection was made. */
    public boolean connect(ServerPlayer player, BlockPos pos, Vec3 click) {
        ServerLevel sl = (ServerLevel) level;
        checkOut();
        if (pulled > 0) {
            deny(player, "already_out");
            return false;
        }
        BlockState state = sl.getBlockState(pos);
        if (pos.equals(worldPosition) || !(state.getBlock() instanceof ElectricalDeviceBlock<?> device)
                || !WallSpace.sameSpace(sl, worldPosition, pos)) {
            deny(player, "invalid_target");
            return false;
        }
        WireType type = wireType();
        if (type == null) {
            deny(player, "empty");
            return false;
        }
        CableReelBlock b = block();
        InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
        device.ensureNodesExist(sl, pos, state);
        b.ensureNodesExist(sl, worldPosition, getBlockState());
        InWorldNode to, toReturn = null;
        if (b.cord) {
            InWorldNode[] ends = CordPair.ends(sl, pos);
            if (ends == null) {
                deny(player, "cord_needs_socket");
                return false;
            }
            for (int i = 0; i < 4; i++)
                if (!sd.getConnections(new InWorldNode(i, pos)).isEmpty()) {
                    deny(player, "socket_busy");
                    return false;
                }
            to = ends[0];
            toReturn = ends[1];
        } else {
            to = InWorldNode.closestNode(sl, pos, state, 1.5F, click);
            if (to == null || !device.isNodeAccessible(sl, pos, state, to.id())) {
                deny(player, "invalid_target");
                return false;
            }
            if (sd.isConnected(outlet(0), to)) {
                deny(player, "already_out");
                return false;
            }
        }
        double dist = WallSpace.distance(sl, outletWorld(), WallSpace.toWorld(sl, WallSpace.nodePos(sl, to)));
        double len = ReelMath.pullLength(dist, stored);
        if (len < 0) {
            deny(player, "too_short", fmt(dist), fmt(stored));
            return false;
        }
        target = to;
        pulled = len;
        stored -= len;
        grab = 0;
        sd.connect(outlet(0), to, WireData.ofLength(type, (float) len));
        if (toReturn != null)
            sd.connect(outlet(1), toReturn, WireData.ofLength(type, (float) len));
        sync();
        AllSoundEvents.WRENCH_REMOVE.playOnServer(sl, pos);
        player.displayClientMessage(Component.translatable("voltworks.cable_reel.connected", fmt(len), fmt(stored)), true);
        return true;
    }

    // ---- winding back ----------------------------------------------------------------------------------------------

    public void retractByPlayer(ServerPlayer player) {
        boolean out = target != null || grab != 0;
        grab = 0;
        windIn();
        setChanged();
        if (out) {
            level.playSound(null, worldPosition, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.BLOCKS, 0.9f, 0.7f);
            player.displayClientMessage(Component.translatable("voltworks.cable_reel.retracted", fmt(stored)), true);
        } else {
            player.displayClientMessage(Component.translatable("voltworks.cable_reel.status", fmt(stored), fmt(VoltworksConfig.reelCapacity())), true);
        }
    }

    /**
     * Called before Electro Energetics removes a connection that starts on this reel's outlet (target broken, wire
     * cutters, empty spool, ...). Winds the cable back instead, so the caller drops nothing.
     */
    boolean windBackFor(InWorldNodeConnection connection) {
        if (target == null)
            return false;
        InWorldNode other = connection.node1().sourcePos().equals(worldPosition) && block().isOutlet(connection.node1().id())
                ? connection.node2() : connection.node1();
        if (!other.sourcePos().equals(target.sourcePos()))
            return false;
        windIn();
        level.playSound(null, worldPosition, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.BLOCKS, 0.9f, 0.7f);
        return true;
    }

    /** Removes the pulled-out connection without drops and puts its length back on the drum. */
    public void windIn() {
        if (target == null && pulled <= 0)
            return;
        InWorldNode to = target;
        target = null;
        stored += pulled;
        pulled = 0;
        if (to != null && level instanceof ServerLevel sl) {
            InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
            removeQuietly(sd, new InWorldNodeConnection(outlet(0), to));
            if (block().cord)
                removeQuietly(sd, new InWorldNodeConnection(outlet(1), new InWorldNode(to.id() + 1, to.sourcePos())));
        }
        sync();
    }

    private static void removeQuietly(InfrastructureSavedData sd, InWorldNodeConnection c) {
        if (sd.getConnectionData(c) != null)
            sd.removeConnectionNoDrops(c);
    }

    /** If the connection vanished some other way, the cable counts as wound back in. */
    private void checkOut() {
        if (target == null || !(level instanceof ServerLevel sl))
            return;
        if (InfrastructureSavedData.load(sl).getConnectionData(new InWorldNodeConnection(outlet(0), target)) == null)
            windIn();
    }

    /** Block broken: the cable is wound in, then everything on the drum drops as wire, like a removed wire would. */
    public void windInAndDrop(ServerLevel sl) {
        grab = 0;
        windIn();
        WireType type = wireType();
        if (type == null || stored <= ReelMath.EPS)
            return;
        double spool = VoltworksConfig.reelSpoolLength();
        Item item;
        int count;
        if (CEEConfigs.server().alternateWirePlacement.get() || type.getDrops() == null || type.getDrops() == Items.AIR) {
            item = type.getSpooledItem();
            count = (int) Math.floor(stored / spool + ReelMath.EPS);
        } else {
            item = type.getDrops();
            count = ReelMath.itemsFor(stored, spool, CEEConfigs.server().wiresPerSpool.get());
        }
        stored = 0;
        while (count > 0) {
            int n = Math.min(count, item.getDefaultMaxStackSize());
            Containers.dropItemStack(sl, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, new ItemStack(item, n));
            count -= n;
        }
    }

    // ---- misc ------------------------------------------------------------------------------------------------------

    private void deny(ServerPlayer player, String key, Object... args) {
        AllSoundEvents.DENY.playOnServer(level, worldPosition);
        player.displayClientMessage(Component.translatable("voltworks.cable_reel." + key, args).withStyle(ChatFormatting.RED), true);
    }

    public static String fmt(double blocks) {
        return String.format(Locale.ROOT, "%.1f", blocks);
    }

    private void sync() {
        setChanged();
        if (level != null)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(getBlockState().getBlock().getName()));
        WireType type = wireType();
        if (type != null && (stored + pulled > ReelMath.EPS || block().cord))
            tooltip.add(Component.literal(" ").append(type.getSpooledItem().getDescription().copy().withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal(" ").append(Component.translatable("voltworks.cable_reel.goggles.wound",
                fmt(stored), fmt(VoltworksConfig.reelCapacity())).withStyle(ChatFormatting.GRAY)));
        if (pulled > 0)
            tooltip.add(Component.literal(" ").append(Component.translatable("voltworks.cable_reel.goggles.pulled", fmt(pulled)).withStyle(ChatFormatting.GOLD)));
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("Stored", stored);
        tag.putDouble("Pulled", pulled);
        if (wireTypeId != null)
            tag.putString("WireType", wireTypeId.toString());
        if (target != null) {
            tag.putInt("TargetId", target.id());
            tag.putLong("TargetPos", target.sourcePos().asLong());
        }
        if (grab != 0)
            tag.putLong("Grab", grab);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = tag.getDouble("Stored");
        pulled = tag.getDouble("Pulled");
        wireTypeId = tag.contains("WireType") ? ResourceLocation.tryParse(tag.getString("WireType")) : null;
        target = tag.contains("TargetPos") ? new InWorldNode(tag.getInt("TargetId"), BlockPos.of(tag.getLong("TargetPos"))) : null;
        grab = tag.getLong("Grab");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
