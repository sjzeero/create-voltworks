package io.github.sjzeero.voltworks.reel;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.VoltworksConfig;
import io.github.sjzeero.voltworks.cord.CordPair;
import io.github.sjzeero.voltworks.cord.LooseCords;
import io.github.sjzeero.voltworks.cord.SocketBlock;
import io.github.sjzeero.voltworks.cord.SocketPlugs;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class CableReelBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private @Nullable ResourceLocation wireTypeId;
    private double stored;
    private double pulled;
    private long lastSyncTick;
    private double soundAccumulator;
    private long lastSoundTick;
    private long grab;
    private @Nullable UUID loose;
    private @Nullable BlockPos looseAt;

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

    public boolean isLooseEnd(UUID id) {
        return id.equals(loose);
    }

    public boolean hasLooseEnd() {
        return loose != null;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        updateFill();
    }

    private void updateFill() {
        if (level == null || level.isClientSide || isRemoved())
            return;
        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof CableReelBlock) || !state.hasProperty(CableReelBlock.FILL))
            return;
        int fill = ReelMath.fillLevel(stored, VoltworksConfig.reelCapacity());
        if (state.getValue(CableReelBlock.FILL) != fill)
            level.setBlock(worldPosition, state.setValue(CableReelBlock.FILL, fill), Block.UPDATE_CLIENTS);
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

    public Vec3 outletWorld() {
        Vec3 local = block().localNode(getBlockState(), block().outletId());
        return WallSpace.toWorld(level, Vec3.atLowerCornerOf(worldPosition).add(local));
    }

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

    public void grab(ServerPlayer player) {
        checkOut();
        checkLoose();
        if (pulled > 0) {
            deny(player, "already_out");
            return;
        }
        if (loose != null) {
            deny(player, "end_on_floor");
            return;
        }
        if (stored < 0.5 || wireType() == null) {
            deny(player, "empty");
            return;
        }
        giveEnd(player);
        level.playSound(null, worldPosition, SoundEvents.FISHING_BOBBER_THROW, SoundSource.BLOCKS, 0.7f, 0.9f);
    }

    private void giveEnd(ServerPlayer player) {
        long id;
        do id = level.random.nextLong(); while (id == 0);
        grab = id;
        sync();
        player.setItemInHand(InteractionHand.MAIN_HAND, ReelPlugItem.create(worldPosition, id, block().cord));
        player.displayClientMessage(Component.translatable("voltworks.cable_reel.grabbed", fmt(stored)), true);
    }

    public void dropLoose(Vec3 at, Vec3 motion) {
        if (!(level instanceof ServerLevel sl))
            return;
        removeLoose();
        grab = 0;
        if (wireType() == null || stored <= ReelMath.EPS) {
            sync();
            return;
        }
        Vec3 outlet = outletWorld();
        Vec3 pos = outlet.add(at.subtract(outlet).scale(ReelMath.reachScale(at.distanceTo(outlet), stored)));
        CableEndEntity end = new CableEndEntity(sl, worldPosition, pos, motion);
        boolean added = sl.addFreshEntity(end);
        Voltworks.LOGGER.info("[voltworks] reel {} dropped loose end: type={} id={} uuid={} at={} added={}",
                worldPosition, EntityType.getKey(end.getType()), end.getId(), end.getUUID(), pos, added);
        if (added) {
            loose = end.getUUID();
            looseAt = end.blockPosition();
        }
        sync();
    }

    void pickUpLoose(ServerPlayer player, CableEndEntity end) {
        if (!player.getMainHandItem().isEmpty()) {
            deny(player, "hands_full");
            return;
        }
        loose = null;
        looseAt = null;
        end.discard();
        if (wireType() == null || stored <= ReelMath.EPS) {
            sync();
            return;
        }
        level.playSound(null, end.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 0.8f);
        giveEnd(player);
    }

    void looseSeen(BlockPos pos) {
        if (!pos.equals(looseAt)) {
            looseAt = pos;
            super.setChanged();
        }
    }

    void looseGone(UUID id) {
        if (id.equals(loose)) {
            loose = null;
            looseAt = null;
            sync();
        }
    }

    private void removeLoose() {
        if (loose == null)
            return;
        UUID id = loose;
        loose = null;
        looseAt = null;
        if (level instanceof ServerLevel sl && sl.getEntity(id) instanceof CableEndEntity end)
            end.discard();
        setChanged();
    }

    private void checkLoose() {
        if (loose == null || !(level instanceof ServerLevel sl) || sl.getEntity(loose) != null)
            return;
        if (looseAt == null || sl.areEntitiesLoaded(ChunkPos.asLong(looseAt))) {
            loose = null;
            looseAt = null;
            sync();
        }
    }

    public boolean connect(ServerPlayer player, BlockPos pos, Vec3 click) {
        ServerLevel sl = (ServerLevel) level;
        checkOut();
        if (pulled > 0) {
            deny(player, "already_out");
            return false;
        }
        BlockState state = sl.getBlockState(pos);
        if (pos.equals(worldPosition) || !(state.getBlock() instanceof ElectricalDeviceBlock<?> device)) {
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
        if (!outgoing(sd).isEmpty()) {
            deny(player, "already_out");
            return false;
        }
        InWorldNode to, toReturn = null;
        if (b.cord) {
            InWorldNode[] ends = CordPair.ends(sl, pos, click);
            if (ends == null) {
                deny(player, "cord_needs_socket");
                return false;
            }
            boolean socket = state.getBlock() instanceof SocketBlock;
            for (int i = socket ? 0 : ends[0].id(); i < (socket ? 4 : ends[0].id() + 2); i++)
                if (!sd.getConnections(new InWorldNode(i, pos)).isEmpty()) {
                    deny(player, "socket_busy");
                    return false;
                }
            if (LooseCords.get(sl).isAnchor(pos, ends[0].id())) {
                deny(player, "socket_busy");
                return false;
            }
            to = ends[0];
            toReturn = ends[1];
            plugIn(sl, pos);
        } else {
            to = InWorldNode.closestNode(sl, click, 1.5F);
            if (to == null || !to.sourcePos().equals(pos))
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
            if (b.cord)
                unplug(sl, pos);
            return false;
        }
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

    public static @Nullable CableReelBlockEntity pluggedInto(ServerLevel sl, BlockPos socket) {
        return pluggedInto(sl, socket, SocketBlock.CORD_POSITIVE);
    }

    public static @Nullable CableReelBlockEntity pluggedInto(ServerLevel sl, BlockPos socket, int supply) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
        for (int id : new int[]{supply, supply + 1})
            for (InWorldNodeConnection c : sd.getConnections(new InWorldNode(id, socket))) {
                InWorldNode other = c.node1().sourcePos().equals(socket) && c.node1().id() == id ? c.node2() : c.node1();
                if (sl.isLoaded(other.sourcePos()) && sl.getBlockEntity(other.sourcePos()) instanceof CableReelBlockEntity be
                        && be.block().cord && be.block().isOutlet(other.id()))
                    return be;
            }
        return null;
    }

    public void unplugByPlayer(ServerPlayer player) {
        if (!(level instanceof ServerLevel sl))
            return;
        List<InWorldNodeConnection> out = outgoing(InfrastructureSavedData.load(sl));
        if (out.isEmpty())
            return;
        Vec3 end = farEnd(out.getFirst());
        BlockPos socket = far(out.getFirst()).sourcePos();
        windIn();
        removeLoose();
        AllSoundEvents.WRENCH_REMOVE.playOnServer(sl, socket);
        if (player.getMainHandItem().isEmpty() && wireType() != null && stored > ReelMath.EPS) {
            giveEnd(player);
        } else if (end != null) {
            dropLoose(end, Vec3.ZERO);
            player.displayClientMessage(Component.translatable("voltworks.cable_reel.unplugged"), true);
        }
        setChanged();
    }

    public void retractByPlayer(ServerPlayer player) {
        boolean out = pulled > 0 || grab != 0 || loose != null || (level instanceof ServerLevel sl && !outgoing(InfrastructureSavedData.load(sl)).isEmpty());
        grab = 0;
        removeLoose();
        windIn();
        setChanged();
        if (out) {
            level.playSound(null, worldPosition, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.BLOCKS, 0.9f, 0.7f);
            player.displayClientMessage(Component.translatable("voltworks.cable_reel.retracted", fmt(stored)), true);
        } else {
            player.displayClientMessage(Component.translatable("voltworks.cable_reel.status", fmt(stored), fmt(VoltworksConfig.reelCapacity())), true);
        }
    }

    private List<InWorldNodeConnection> outgoing(InfrastructureSavedData sd) {
        List<InWorldNodeConnection> out = new ArrayList<>(sd.getConnections(outlet(0)));
        if (block().cord)
            out.addAll(sd.getConnections(outlet(1)));
        return out;
    }

    boolean windBackFor(InWorldNodeConnection connection) {
        Vec3 end = farEnd(connection);
        windIn();
        if (end != null) {
            dropLoose(end, Vec3.ZERO);
            level.playSound(null, BlockPos.containing(end), SoundEvents.LEASH_KNOT_BREAK, SoundSource.BLOCKS, 0.7f, 1.2f);
        }
        return true;
    }

    private @Nullable Vec3 farEnd(InWorldNodeConnection connection) {
        if (!(level instanceof ServerLevel sl))
            return null;
        InWorldNode far = far(connection);
        try {
            return WallSpace.toWorld(sl, WallSpace.nodePos(sl, far));
        } catch (RuntimeException e) {
            return WallSpace.toWorld(sl, Vec3.atCenterOf(far.sourcePos()));
        }
    }

    public void windIn() {
        stored += pulled;
        pulled = 0;
        if (level instanceof ServerLevel sl) {
            InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
            Set<BlockPos> sockets = new HashSet<>();
            for (InWorldNodeConnection c : outgoing(sd)) {
                sockets.add(far(c).sourcePos());
                if (sd.getConnectionData(c) != null)
                    sd.removeConnectionNoDrops(c);
            }
            if (block().cord)
                for (BlockPos p : sockets)
                    unplug(sl, p);
        }
        sync();
    }

    private InWorldNode far(InWorldNodeConnection c) {
        return c.node1().sourcePos().equals(worldPosition) && block().isOutlet(c.node1().id()) ? c.node2() : c.node1();
    }

    private void plugIn(ServerLevel sl, BlockPos pos) {
        SocketPlugs.plugTowards(sl, pos, outletWorld());
    }

    private static void unplug(ServerLevel sl, BlockPos pos) {
        if (!sl.isLoaded(pos))
            return;
        BlockState st = sl.getBlockState(pos);
        if (st.getBlock() instanceof SocketBlock && st.getValue(SocketBlock.PLUG) != SocketBlock.Plug.NONE)
            sl.setBlock(pos, st.setValue(SocketBlock.PLUG, SocketBlock.Plug.NONE), Block.UPDATE_ALL);
    }

    private void checkOut() {
        if (pulled <= 0 || !(level instanceof ServerLevel sl))
            return;
        if (InfrastructureSavedData.load(sl).getConnections(outlet(0)).isEmpty())
            windIn();
    }

    boolean follow(InfrastructureSavedData sd, InWorldNodeConnection connection, int outletId, WireData data) {
        Vec3 a = sd.getNodePosition(connection.node1()), b = sd.getNodePosition(connection.node2());
        if (a == null || b == null)
            return false;
        double distance = a.distanceTo(b);
        double total = stored + pulled;
        if (ReelMath.overstretched(distance, total))
            return true;
        double want = ReelMath.followLength(distance, total);
        if (outletId == block().outletId() && Math.abs(want - pulled) > 1.0E-4) {
            double delta = want - pulled;
            pulled = want;
            stored = Math.max(0, total - want);
            changedByFollow(delta);
        }
        data.length = want;
        return false;
    }

    private void changedByFollow(double delta) {
        setChanged();
        long now = level.getGameTime();
        if (now - lastSyncTick >= 10) {
            lastSyncTick = now;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        soundAccumulator += delta;
        if (Math.abs(soundAccumulator) >= 1.0 && now - lastSoundTick >= 4) {
            lastSoundTick = now;
            boolean out = soundAccumulator > 0;
            soundAccumulator = 0;
            level.playSound(null, worldPosition, SoundEvents.FISHING_BOBBER_THROW, SoundSource.BLOCKS, 0.25f, out ? 1.4f : 0.9f);
        }
    }

    public void windInAndDrop(ServerLevel sl) {
        grab = 0;
        removeLoose();
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
        else if (loose != null)
            tooltip.add(Component.literal(" ").append(Component.translatable("voltworks.cable_reel.goggles.on_floor").withStyle(ChatFormatting.GOLD)));
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("Stored", stored);
        tag.putDouble("Pulled", pulled);
        if (wireTypeId != null)
            tag.putString("WireType", wireTypeId.toString());
        if (grab != 0)
            tag.putLong("Grab", grab);
        if (loose != null)
            tag.putUUID("LooseEnd", loose);
        if (looseAt != null)
            tag.putLong("LooseAt", looseAt.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = tag.getDouble("Stored");
        pulled = tag.getDouble("Pulled");
        wireTypeId = tag.contains("WireType") ? ResourceLocation.tryParse(tag.getString("WireType")) : null;
        grab = tag.getLong("Grab");
        loose = tag.hasUUID("LooseEnd") ? tag.getUUID("LooseEnd") : null;
        looseAt = tag.contains("LooseAt") ? BlockPos.of(tag.getLong("LooseAt")) : null;
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
