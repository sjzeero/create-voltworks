package io.github.sjzeero.voltworks.cord;

import com.george_vi.electroenergetics.CEETags;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.simibubi.create.AllSoundEvents;
import io.github.sjzeero.voltworks.reel.CableEndEntity;
import io.github.sjzeero.voltworks.reel.CableReelBlockEntity;
import io.github.sjzeero.voltworks.reel.ReelMath;
import io.github.sjzeero.voltworks.reel.ReelPlugItem;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.registry.VoltworksBlocks;
import io.github.sjzeero.voltworks.registry.VoltworksItems;
import io.github.sjzeero.voltworks.registry.VoltworksWireTypes;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class LooseCords extends SavedData {
    private static final String NAME = "voltworks_loose_cords";
    private static final int CHECK_TICKS = 20;
    private static final double STRETCH = 1.0;

    public enum Kind {CORD, STRIP}

    public enum State {PLUGGED, HELD, LYING, STRIP}

    public static final class End {
        State state;
        BlockPos pos = BlockPos.ZERO;
        int supply;
        @Nullable UUID who;
        long grab;

        static End plugged(BlockPos pos, int supply) {
            End e = new End();
            e.state = State.PLUGGED;
            e.pos = pos.immutable();
            e.supply = supply;
            return e;
        }

        static End strip(UUID strip) {
            End e = new End();
            e.state = State.STRIP;
            e.who = strip;
            return e;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("State", state.name());
            t.putLong("Pos", pos.asLong());
            t.putInt("Supply", supply);
            if (who != null)
                t.putUUID("Who", who);
            t.putLong("Grab", grab);
            return t;
        }

        static End load(CompoundTag t) {
            End e = new End();
            try {
                e.state = State.valueOf(t.getString("State"));
            } catch (IllegalArgumentException ex) {
                e.state = State.LYING;
            }
            e.pos = BlockPos.of(t.getLong("Pos"));
            e.supply = t.getInt("Supply");
            e.who = t.hasUUID("Who") ? t.getUUID("Who") : null;
            e.grab = t.getLong("Grab");
            return e;
        }
    }

    public static final class Cord {
        final UUID id;
        Kind kind;
        double reach;
        final End[] ends = new End[2];

        Cord(UUID id) {
            this.id = id;
        }
    }

    public static final class Strip {
        final UUID id;
        @Nullable BlockPos socket;
        Vec3 at = Vec3.ZERO;
        double yaw;
        final Supplier<StripLinks.Placement> link = () -> new StripLinks.Placement(at, yaw);

        Strip(UUID id) {
            this.id = id;
        }

        Vec3 input() {
            return link.get().node(StripGeometry.INPUT);
        }
    }

    private final ServerLevel level;
    private final Map<UUID, Cord> cords = new LinkedHashMap<>();
    private final Map<UUID, Strip> strips = new LinkedHashMap<>();
    private final Map<String, Double> reaches = new HashMap<>();
    private final Set<UUID> quiet = new HashSet<>();
    private final Set<UUID> quietStrips = new HashSet<>();
    private final Set<BlockPos> legacy = new LinkedHashSet<>();

    private LooseCords(ServerLevel level) {
        this.level = level;
        StripLinks.touch(level);
    }

    public static LooseCords get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(() -> new LooseCords(level),
                (tag, provider) -> load(level, tag)), NAME);
    }

    private static LooseCords load(ServerLevel level, CompoundTag tag) {
        LooseCords d = new LooseCords(level);
        for (Tag t : tag.getList("Cords", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (!c.hasUUID("Id"))
                continue;
            Cord cord = new Cord(c.getUUID("Id"));
            cord.kind = "STRIP".equals(c.getString("Kind")) ? Kind.STRIP : Kind.CORD;
            cord.reach = c.getDouble("Reach");
            cord.ends[0] = End.load(c.getCompound("End0"));
            cord.ends[1] = End.load(c.getCompound("End1"));
            d.cords.put(cord.id, cord);
        }
        CompoundTag r = tag.getCompound("Reaches");
        for (String k : r.getAllKeys())
            d.reaches.put(k, r.getDouble(k));
        for (Tag t : tag.getList("Strips", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (!c.hasUUID("Id"))
                continue;
            Strip strip = new Strip(c.getUUID("Id"));
            strip.socket = c.contains("Socket") ? BlockPos.of(c.getLong("Socket")) : null;
            strip.at = new Vec3(c.getDouble("X"), c.getDouble("Y"), c.getDouble("Z"));
            strip.yaw = c.getDouble("Yaw");
            d.strips.put(strip.id, strip);
            if (strip.socket != null)
                StripLinks.put(level, strip.socket, strip.link);
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        ListTag list = new ListTag();
        for (Cord c : cords.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", c.id);
            t.putString("Kind", c.kind.name());
            t.putDouble("Reach", c.reach);
            t.put("End0", c.ends[0].save());
            t.put("End1", c.ends[1].save());
            list.add(t);
        }
        tag.put("Cords", list);
        CompoundTag r = new CompoundTag();
        reaches.forEach(r::putDouble);
        tag.put("Reaches", r);
        ListTag sl = new ListTag();
        for (Strip strip : strips.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", strip.id);
            if (strip.socket != null)
                t.putLong("Socket", strip.socket.asLong());
            t.putDouble("X", strip.at.x);
            t.putDouble("Y", strip.at.y);
            t.putDouble("Z", strip.at.z);
            t.putDouble("Yaw", strip.yaw);
            sl.add(t);
        }
        tag.put("Strips", sl);
        return tag;
    }

    public static boolean isCutters(ItemStack stack) {
        return !stack.isEmpty() && stack.is(CEETags.WIRE_CUTTERS);
    }

    private static WireType type() {
        return VoltworksWireTypes.COPPER_CORD.get();
    }

    private static String key(InWorldNode a, InWorldNode b) {
        String x = a.sourcePos().asLong() + ":" + a.id(), y = b.sourcePos().asLong() + ":" + b.id();
        return x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x;
    }

    public static Vec3 plugWorld(Level level, BlockPos pos, int supply) {
        try {
            return WallSpace.toWorld(level, WallSpace.nodePos(level, new InWorldNode(supply, pos)));
        } catch (RuntimeException e) {
            return WallSpace.toWorld(level, Vec3.atCenterOf(pos));
        }
    }

    private @Nullable ServerPlayer player(@Nullable UUID id) {
        return id == null ? null : level.getServer().getPlayerList().getPlayer(id);
    }

    private @Nullable CableEndEntity entity(@Nullable UUID id) {
        return id != null && level.getEntity(id) instanceof CableEndEntity e ? e : null;
    }

    private @Nullable Vec3 where(End e) {
        return switch (e.state) {
            case PLUGGED -> level.isLoaded(e.pos) ? plugWorld(level, e.pos, e.supply) : null;
            case HELD -> {
                ServerPlayer p = player(e.who);
                yield p != null && p.level() == level ? p.position().add(0, 1, 0) : null;
            }
            case LYING -> {
                CableEndEntity ent = entity(e.who);
                yield ent != null ? ent.position() : Vec3.atCenterOf(e.pos);
            }
            case STRIP -> {
                Strip strip = e.who == null ? null : strips.get(e.who);
                yield strip == null ? null : strip.input();
            }
        };
    }

    private @Nullable PowerStripEntity stripEntity(@Nullable UUID id) {
        return id != null && level.getEntity(id) instanceof PowerStripEntity e ? e : null;
    }

    private @Nullable Strip stripAt(BlockPos socket) {
        for (Strip strip : strips.values())
            if (socket.equals(strip.socket))
                return strip;
        return null;
    }

    private @Nullable Cord stripCordOf(Strip strip) {
        for (Cord c : cords.values())
            if (c.kind == Kind.STRIP && c.ends[0].state == State.STRIP && strip.id.equals(c.ends[0].who))
                return c;
        return null;
    }

    private void linkEnd(CableEndEntity ent, Cord c, End o) {
        ServerPlayer holder = o.state == State.HELD ? player(o.who) : null;
        PowerStripEntity strip = o.state == State.STRIP ? stripEntity(o.who) : null;
        ent.setOther(o.state == State.PLUGGED ? o.pos : BlockPos.ZERO, o.state == State.PLUGGED ? o.supply : -1,
                holder != null ? holder.getId() : -1, (float) c.reach);
        ent.setOtherStrip(strip != null ? strip.getId() : -1);
    }

    public boolean isAnchor(BlockPos pos, int supply) {
        for (Cord c : cords.values())
            for (End e : c.ends)
                if (e.state == State.PLUGGED && e.supply == (supply & ~1) && e.pos.equals(pos) && !(c.kind == Kind.STRIP && e == c.ends[0]))
                    return true;
        return false;
    }

    public boolean hasAnchor(BlockPos pos) {
        for (Cord c : cords.values())
            for (End e : c.ends)
                if (e.state == State.PLUGGED && e.pos.equals(pos) && !(c.kind == Kind.STRIP && e == c.ends[0]))
                    return true;
        return false;
    }

    private @Nullable Cord stripCord(BlockPos strip) {
        for (Cord c : cords.values())
            if (c.kind == Kind.STRIP && c.ends[0].state == State.PLUGGED && c.ends[0].pos.equals(strip))
                return c;
        return null;
    }

    public boolean free(BlockPos pos, int supply) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        return sd.getConnections(new InWorldNode(supply, pos)).isEmpty() && sd.getConnections(new InWorldNode(supply + 1, pos)).isEmpty()
                && !isAnchor(pos, supply);
    }

    public static @Nullable InWorldNodeConnection cordAt(ServerLevel sl, BlockPos pos, int supply) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
        for (int id : new int[]{supply, supply + 1})
            for (InWorldNodeConnection c : sd.getConnections(new InWorldNode(id, pos))) {
                WireData d = sd.getConnectionData(c);
                if (d == null || !CordPair.isCord(d.wireType()))
                    continue;
                return CordPair.isReturn(c) ? CordPair.sibling(c) : c;
            }
        return null;
    }

    private static InWorldNode far(InWorldNodeConnection c, BlockPos pos, int supply) {
        return c.node1().sourcePos().equals(pos) && (c.node1().id() & ~1) == supply ? c.node2() : c.node1();
    }

    private void removePair(InWorldNodeConnection supplyStrand) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        InWorldNodeConnection ret = CordPair.sibling(supplyStrand);
        if (sd.getConnectionData(ret) != null)
            sd.removeConnectionNoDrops(ret);
        if (sd.getConnectionData(supplyStrand) != null)
            sd.removeConnectionNoDrops(supplyStrand);
    }

    private void plugOut(BlockPos pos) {
        if (!level.isLoaded(pos))
            return;
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof SocketBlock) || st.getValue(SocketBlock.PLUG) == SocketBlock.Plug.NONE)
            return;
        if (cordAt(level, pos, SocketBlock.CORD_POSITIVE) != null || isAnchor(pos, SocketBlock.CORD_POSITIVE)
                || CableReelBlockEntity.pluggedInto(level, pos) != null)
            return;
        level.setBlock(pos, st.setValue(SocketBlock.PLUG, SocketBlock.Plug.NONE), Block.UPDATE_ALL);
    }

    private static void message(ServerPlayer p, String key, Object... args) {
        p.displayClientMessage(Component.translatable(key, args), true);
    }

    private static void deny(ServerPlayer p, String key, Object... args) {
        p.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
        AllSoundEvents.DENY.playOnServer(p.level(), p.blockPosition());
    }

    private void click(Vec3 at) {
        AllSoundEvents.WRENCH_REMOVE.playOnServer(level, BlockPos.containing(at));
    }

    private long newGrab() {
        long id;
        do id = level.random.nextLong(); while (id == 0);
        return id;
    }

    public static boolean isLooseCord(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains("LooseCord");
    }

    private static @Nullable CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || !data.contains("LooseCord") ? null : data.copyTag();
    }

    private ItemStack heldStack(Cord c, int end) {
        ItemStack stack = ReelPlugItem.look(true);
        CompoundTag t = new CompoundTag();
        t.putBoolean("Cord", true);
        t.putUUID("LooseCord", c.id);
        t.putInt("End", end);
        t.putLong("Grab", c.ends[end].grab);
        t.putDouble("Reach", c.reach);
        End o = c.ends[1 - end];
        t.putLong("OtherPos", o.pos.asLong());
        t.putInt("OtherSupply", o.state == State.PLUGGED ? o.supply : -1);
        if (o.state == State.STRIP && o.who != null)
            t.putUUID("OtherStrip", o.who);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        return stack;
    }

    private @Nullable Cord heldCord(ItemStack stack, Player player) {
        CompoundTag t = tag(stack);
        if (t == null || !t.hasUUID("LooseCord"))
            return null;
        Cord c = cords.get(t.getUUID("LooseCord"));
        int end = t.getInt("End");
        if (c == null || end < 0 || end > 1)
            return null;
        End e = c.ends[end];
        return e.state == State.HELD && player.getUUID().equals(e.who) && e.grab == t.getLong("Grab") ? c : null;
    }

    private static int heldEnd(ItemStack stack) {
        CompoundTag t = tag(stack);
        return t == null ? 0 : t.getInt("End");
    }

    private void giveEnd(Cord c, int end, ServerPlayer p) {
        End e = new End();
        e.state = State.HELD;
        e.who = p.getUUID();
        e.grab = newGrab();
        e.pos = p.blockPosition();
        c.ends[end] = e;
        p.setItemInHand(InteractionHand.MAIN_HAND, heldStack(c, end));
        setDirty();
    }

    private void lay(Cord c, int end, Vec3 at, Vec3 motion) {
        End o = c.ends[1 - end];
        Vec3 anchor = o.state == State.LYING && entity(o.who) == null ? null : where(o);
        Vec3 pos = at;
        if (anchor != null)
            pos = anchor.add(at.subtract(anchor).scale(ReelMath.reachScale(at.distanceTo(anchor), c.reach)));
        CableEndEntity ent = new CableEndEntity(level, c.id, end, (float) c.reach, pos, motion);
        End e = new End();
        e.state = State.LYING;
        e.pos = BlockPos.containing(pos);
        c.ends[end] = e;
        if (level.addFreshEntity(ent))
            e.who = ent.getUUID();
        refresh(c);
        setDirty();
    }

    private void refresh(Cord c) {
        for (int i = 0; i < 2; i++) {
            End e = c.ends[i], o = c.ends[1 - i];
            if (e.state == State.LYING) {
                CableEndEntity ent = entity(e.who);
                if (ent != null)
                    linkEnd(ent, c, o);
            } else if (e.state == State.HELD) {
                ServerPlayer p = player(e.who);
                if (p != null && heldCord(p.getMainHandItem(), p) == c && heldEnd(p.getMainHandItem()) == i)
                    p.setItemInHand(InteractionHand.MAIN_HAND, heldStack(c, i));
            }
        }
    }

    private void takeAway(End e) {
        if (e.state == State.HELD) {
            ServerPlayer p = player(e.who);
            if (p != null)
                for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                    ItemStack s = p.getInventory().getItem(i);
                    CompoundTag t = tag(s);
                    if (t != null && t.getLong("Grab") == e.grab)
                        p.getInventory().setItem(i, ItemStack.EMPTY);
                }
        } else if (e.state == State.LYING) {
            CableEndEntity ent = entity(e.who);
            if (e.who != null)
                quiet.add(e.who);
            if (ent != null)
                ent.discard();
        }
    }

    public boolean useOutlet(ServerLevel sl, BlockPos pos, int supply, ServerPlayer p) {
        Strip strip = supply == SocketBlock.CORD_POSITIVE ? stripAt(pos) : null;
        if (strip != null) {
            Vec3 at = plugWorld(sl, pos, supply);
            detachStrip(strip, p.getMainHandItem().isEmpty() ? p : null, false);
            click(at);
            message(p, "voltworks.power_strip.unplugged");
            return true;
        }
        CableReelBlockEntity reel = CableReelBlockEntity.pluggedInto(sl, pos, supply);
        if (reel != null) {
            reel.unplugByPlayer(p);
            return true;
        }
        Vec3 at = plugWorld(sl, pos, supply);
        for (Cord c : new ArrayList<>(cords.values()))
            for (int i = 0; i < 2; i++) {
                End e = c.ends[i];
                if (e.state != State.PLUGGED || !e.pos.equals(pos) || e.supply != supply || (c.kind == Kind.STRIP && i == 0))
                    continue;
                free(c, i, p, at);
                plugOut(pos);
                click(at);
                message(p, "voltworks.cord.carried");
                return true;
            }
        InWorldNodeConnection strand = cordAt(sl, pos, supply);
        if (strand == null)
            return false;
        Cord c = unlay(strand, pos, supply);
        free(c, 1, p, at);
        plugOut(pos);
        click(at);
        message(p, c.kind == Kind.STRIP ? "voltworks.power_strip.unplugged" : "voltworks.cord.end_unplugged");
        return true;
    }

    private void free(Cord c, int i, @Nullable ServerPlayer p, Vec3 at) {
        if (p != null && p.getMainHandItem().isEmpty() && c.ends[1 - i].state != State.HELD)
            giveEnd(c, i, p);
        else
            lay(c, i, at, Vec3.ZERO);
        refresh(c);
    }

    private Cord unlay(InWorldNodeConnection strand, BlockPos pos, int supply) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        WireData data = sd.getConnectionData(strand);
        InWorldNode other = far(strand, pos, supply);
        Double remembered = reaches.remove(key(strand.node1(), strand.node2()));
        double laid = data == null ? 1 : data.length;
        removePair(strand);
        Cord c = new Cord(UUID.randomUUID());
        BlockState os = level.isLoaded(other.sourcePos()) ? level.getBlockState(other.sourcePos()) : null;
        if (os != null && Outlets.isStripInput(os, other.id())) {
            c.kind = Kind.STRIP;
            c.reach = StripGeometry.CORD_LENGTH;
        } else {
            c.kind = Kind.CORD;
            c.reach = CordReach.reachOf(remembered, laid);
        }
        c.ends[0] = End.plugged(other.sourcePos(), other.id() & ~1);
        c.ends[1] = End.plugged(pos, supply);
        cords.put(c.id, c);
        setDirty();
        return c;
    }

    public boolean plugHeld(ServerPlayer p, ItemStack stack, BlockPos pos, Vec3 click) {
        Cord c = heldCord(stack, p);
        if (c == null)
            return true;
        int end = heldEnd(stack);
        if (c.kind == Kind.STRIP && c.ends[0].state != State.STRIP) {
            stow(c);
            message(p, "voltworks.power_strip.stowed");
            return true;
        }
        BlockState state = level.getBlockState(pos);
        int supply = Outlets.pick(level, pos, click);
        if (supply < 0) {
            deny(p, "voltworks.cord.needs_outlet");
            return false;
        }
        if (c.kind == Kind.STRIP) {
            Strip strip = strips.get(c.ends[0].who);
            if (strip == null) {
                stow(c);
                return true;
            }
            return attachStrip(p, c, strip, pos);
        }
        return plugInto(p, c, end, pos, supply);
    }

    private boolean plugInto(ServerPlayer p, Cord c, int end, BlockPos pos, int supply) {
        End o = c.ends[1 - end];
        BlockState state = level.getBlockState(pos);
        if (o.state == State.PLUGGED && o.pos.equals(pos) && o.supply == supply) {
            lay(c, 1 - end, plugWorld(level, pos, supply), Vec3.ZERO);
            plugOut(pos);
            click(plugWorld(level, pos, supply));
            message(p, "voltworks.cord.carried");
            return false;
        }
        if (!free(pos, supply)) {
            deny(p, "voltworks.cord.outlet_busy");
            return false;
        }
        if (state.getBlock() instanceof ElectricalDeviceBlock<?> b)
            b.ensureNodesExist(level, pos, state);
        Vec3 here = plugWorld(level, pos, supply);
        if (o.state == State.PLUGGED) {
            Vec3 there = plugWorld(level, o.pos, o.supply);
            double d = WallSpace.distance(level, here, there);
            if (!CordReach.fits(d, c.reach)) {
                deny(p, "voltworks.cord.too_far", CableReelBlockEntity.fmt(c.reach), CableReelBlockEntity.fmt(d));
                return false;
            }
            InWorldNode a0 = new InWorldNode(o.supply, o.pos);
            InWorldNode a1 = new InWorldNode(a0.id() + 1, a0.sourcePos());
            InWorldNode b0 = new InWorldNode(supply, pos), b1 = new InWorldNode(supply + 1, pos);
            BlockState ost = level.getBlockState(o.pos);
            if (ost.getBlock() instanceof ElectricalDeviceBlock<?> ob)
                ob.ensureNodesExist(level, o.pos, ost);
            double len = CordReach.laidLength(d, c.reach);
            InfrastructureSavedData sd = InfrastructureSavedData.load(level);
            cords.remove(c.id);
            sd.connect(a0, b0, WireData.ofLength(type(), len));
            sd.connect(a1, b1, WireData.ofLength(type(), len));
            reaches.put(key(a0, b0), c.reach);
            SocketPlugs.plugCord(level, a0, b0, null);
            setDirty();
            click(here);
            message(p, "voltworks.cord.plugged", CableReelBlockEntity.fmt(c.reach));
            return true;
        }
        c.ends[end] = End.plugged(pos, supply);
        if (o.state == State.HELD)
            lay(c, 1 - end, p.position().add(0, 0.5, 0), Vec3.ZERO);
        Vec3 otherAt = where(c.ends[1 - end]);
        if (!StripGeometry.isStripNode(supply))
            SocketPlugs.plugTowards(level, pos, otherAt != null ? otherAt : p.position());
        refresh(c);
        setDirty();
        click(here);
        message(p, "voltworks.cord.end_plugged");
        return true;
    }

    public void sneakHeld(ServerPlayer p, ItemStack stack) {
        Cord c = heldCord(stack, p);
        if (c == null) {
            stack.setCount(0);
            return;
        }
        if (c.kind == Kind.STRIP) {
            stow(c);
            message(p, "voltworks.power_strip.stowed");
        } else {
            stack.setCount(0);
            lay(c, heldEnd(stack), p.position().add(0, 0.5, 0), Vec3.ZERO);
        }
        level.playSound(null, p.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    public void heldTick(ItemStack stack, ServerPlayer p, boolean selected) {
        Cord c = heldCord(stack, p);
        if (c == null) {
            stack.setCount(0);
            return;
        }
        int end = heldEnd(stack);
        if (!selected || p.getMainHandItem() != stack) {
            stack.setCount(0);
            lay(c, end, p.position().add(0, 0.5, 0), Vec3.ZERO);
            return;
        }
        End o = c.ends[1 - end];
        if (o.state != State.PLUGGED || level.getGameTime() % 10 != 0)
            return;
        Vec3 there = where(o);
        if (there == null)
            return;
        double d = WallSpace.distance(level, there, p.position().add(0, 1, 0));
        if (CordReach.snaps(d, c.reach, true)) {
            stack.setCount(0);
            lay(c, end, p.position().add(0, 1, 0), Vec3.ZERO);
            level.playSound(null, p.blockPosition(), SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 0.8f, 1.2f);
            deny(p, "voltworks.cord.slipped");
        } else if (d > c.reach) {
            deny(p, "voltworks.cord.too_far", CableReelBlockEntity.fmt(c.reach), CableReelBlockEntity.fmt(d));
        }
    }

    public void thrown(ItemStack stack, ItemEntity item) {
        CompoundTag t = tag(stack);
        if (t == null || !t.hasUUID("LooseCord"))
            return;
        Cord c = cords.get(t.getUUID("LooseCord"));
        int end = t.getInt("End");
        if (c == null || end < 0 || end > 1 || c.ends[end].state != State.HELD || c.ends[end].grab != t.getLong("Grab"))
            return;
        lay(c, end, item.position(), item.getDeltaMovement());
    }

    public boolean entityTick(CableEndEntity ent) {
        Cord c = cords.get(ent.cordId());
        int end = ent.end();
        if (c == null || end < 0 || end > 1 || c.ends[end].state != State.LYING || !ent.getUUID().equals(c.ends[end].who))
            return false;
        if (ent.tickCount % CHECK_TICKS == 0) {
            BlockPos at = ent.blockPosition();
            if (!at.equals(c.ends[end].pos)) {
                c.ends[end].pos = at;
                setDirty();
            }
            linkEnd(ent, c, c.ends[1 - end]);
        }
        return true;
    }

    public void pickUp(ServerPlayer p, CableEndEntity ent) {
        Cord c = cords.get(ent.cordId());
        int end = ent.end();
        if (c == null || end < 0 || end > 1 || c.ends[end].state != State.LYING || !ent.getUUID().equals(c.ends[end].who)) {
            ent.discard();
            return;
        }
        if (!p.getMainHandItem().isEmpty()) {
            deny(p, "voltworks.cable_reel.hands_full");
            return;
        }
        if (c.ends[1 - end].state == State.HELD) {
            deny(p, "voltworks.cord.other_end_held");
            return;
        }
        quiet.add(ent.getUUID());
        ent.discard();
        level.playSound(null, ent.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 0.8f);
        giveEnd(c, end, p);
        refresh(c);
    }

    public void entityGone(CableEndEntity ent) {
        if (quiet.remove(ent.getUUID()))
            return;
        Cord c = cords.get(ent.cordId());
        int end = ent.end();
        if (c == null || end < 0 || end > 1 || !ent.getUUID().equals(c.ends[end].who))
            return;
        if (c.kind == Kind.STRIP)
            stow(c);
        else
            cut(c);
    }

    public void cut(Cord c) {
        cords.remove(c.id);
        for (End e : c.ends) {
            takeAway(e);
            if (e.state == State.PLUGGED)
                plugOut(e.pos);
        }
        setDirty();
    }

    public void cutEntity(ServerPlayer p, CableEndEntity ent) {
        Cord c = cords.get(ent.cordId());
        if (c == null) {
            ent.discard();
            return;
        }
        if (c.kind == Kind.STRIP)
            stow(c);
        else
            cut(c);
        AllSoundEvents.WRENCH_REMOVE.playOnServer(level, ent.blockPosition());
        message(p, c.kind == Kind.STRIP ? "voltworks.power_strip.stowed" : "voltworks.cord.cut");
    }

    private void stow(Cord c) {
        cords.remove(c.id);
        for (int i = 1; i < 2; i++) {
            End e = c.ends[i];
            takeAway(e);
            if (e.state == State.PLUGGED)
                plugOut(e.pos);
        }
        setDirty();
    }

    public boolean cutAt(ServerLevel sl, BlockPos pos, int supply, ServerPlayer p) {
        Vec3 at = plugWorld(sl, pos, supply);
        Strip strip = supply == SocketBlock.CORD_POSITIVE ? stripAt(pos) : null;
        if (strip != null) {
            detachStrip(strip, null, true);
            click(at);
            message(p, "voltworks.power_strip.stowed");
            return true;
        }
        CableReelBlockEntity reel = CableReelBlockEntity.pluggedInto(sl, pos, supply);
        if (reel != null) {
            reel.unplugByPlayer(p);
            return true;
        }
        for (Cord c : new ArrayList<>(cords.values()))
            for (int i = 0; i < 2; i++) {
                End e = c.ends[i];
                if (e.state == State.PLUGGED && e.pos.equals(pos) && e.supply == supply && !(c.kind == Kind.STRIP && i == 0)) {
                    if (c.kind == Kind.STRIP)
                        stow(c);
                    else
                        cut(c);
                    click(at);
                    message(p, c.kind == Kind.STRIP ? "voltworks.power_strip.stowed" : "voltworks.cord.cut");
                    return true;
                }
            }
        InWorldNodeConnection strand = cordAt(sl, pos, supply);
        if (strand == null)
            return false;
        cutLaid(strand);
        click(at);
        message(p, "voltworks.cord.cut");
        return true;
    }

    public void cutLaid(InWorldNodeConnection strand) {
        InWorldNodeConnection supply = CordPair.isReturn(strand) ? CordPair.sibling(strand) : strand;
        for (InWorldNode n : new InWorldNode[]{supply.node1(), supply.node2()}) {
            Strip strip = StripGeometry.isInput(n.id()) ? stripAt(n.sourcePos()) : null;
            if (strip != null) {
                detachStrip(strip, null, true);
                return;
            }
        }
        reaches.remove(key(supply.node1(), supply.node2()));
        removePair(supply);
        plugOut(supply.node1().sourcePos());
        plugOut(supply.node2().sourcePos());
        setDirty();
    }

    public void blockBroken(ServerLevel sl, BlockPos pos) {
        BlockState st = sl.getBlockState(pos);
        Strip strip = stripAt(pos);
        if (strip != null)
            detachStrip(strip, null, false);
        if (st.getBlock() instanceof PowerStripBlock) {
            Cord own = stripCord(pos);
            if (own != null)
                stow(own);
            InWorldNodeConnection laid = cordAt(sl, pos, PowerStripBlock.INPUT);
            if (laid != null) {
                InWorldNode other = far(laid, pos, PowerStripBlock.INPUT);
                reaches.remove(key(laid.node1(), laid.node2()));
                removePair(laid);
                plugOut(other.sourcePos());
            }
        }
        for (int s : Outlets.of(st)) {
            Vec3 at = plugWorld(sl, pos, s);
            for (Cord c : new ArrayList<>(cords.values()))
                for (int i = 0; i < 2; i++) {
                    End e = c.ends[i];
                    if (e.state == State.PLUGGED && e.pos.equals(pos) && e.supply == s && !(c.kind == Kind.STRIP && i == 0))
                        lay(c, i, at, Vec3.ZERO);
                }
            if (CableReelBlockEntity.pluggedInto(sl, pos, s) != null)
                continue;
            InWorldNodeConnection strand = cordAt(sl, pos, s);
            if (strand != null) {
                Cord c = unlay(strand, pos, s);
                lay(c, 1, at, Vec3.ZERO);
            }
        }
        setDirty();
    }

    public static boolean isStripStrand(ServerLevel sl, InWorldNodeConnection c) {
        for (InWorldNode n : new InWorldNode[]{c.node1(), c.node2()}) {
            if (StripGeometry.isInput(n.id()) && StripLinks.linked(sl, n.sourcePos()))
                return true;
            if (sl.isLoaded(n.sourcePos()) && Outlets.isStripInput(sl.getBlockState(n.sourcePos()), n.id()))
                return true;
        }
        return false;
    }

    public void addStrip(PowerStripEntity e) {
        Strip strip = new Strip(e.getUUID());
        strip.at = e.position();
        strip.yaw = e.yaw();
        strips.put(strip.id, strip);
        setDirty();
    }

    private Strip strip(PowerStripEntity e) {
        Strip strip = strips.get(e.getUUID());
        if (strip == null) {
            addStrip(e);
            strip = strips.get(e.getUUID());
        }
        return strip;
    }

    public boolean plugged(PowerStripEntity e) {
        Strip strip = strips.get(e.getUUID());
        return strip != null && strip.socket != null;
    }

    public boolean attachStrip(@Nullable ServerPlayer p, @Nullable Cord c, Strip strip, BlockPos socket) {
        BlockState st = level.getBlockState(socket);
        if (strip.socket != null || !(st.getBlock() instanceof SocketBlock)) {
            if (p != null)
                deny(p, "voltworks.cord.needs_outlet");
            return false;
        }
        if (!free(socket, SocketBlock.CORD_POSITIVE) || stripAt(socket) != null || CableReelBlockEntity.pluggedInto(level, socket) != null) {
            if (p != null)
                deny(p, "voltworks.cord.outlet_busy");
            return false;
        }
        Vec3 from = strip.input(), here = plugWorld(level, socket, SocketBlock.CORD_POSITIVE);
        double d = from.distanceTo(here);
        if (!CordReach.fits(d, StripGeometry.CORD_LENGTH)) {
            if (p != null)
                deny(p, "voltworks.cord.too_far", CableReelBlockEntity.fmt(StripGeometry.CORD_LENGTH), CableReelBlockEntity.fmt(d));
            return false;
        }
        if (c != null)
            cords.remove(c.id);
        ((ElectricalDeviceBlock<?>) st.getBlock()).ensureNodesExist(level, socket, st);
        strip.socket = socket.immutable();
        StripLinks.put(level, strip.socket, strip.link);
        InWorldNode a0 = new InWorldNode(SocketBlock.CORD_POSITIVE, socket), a1 = new InWorldNode(SocketBlock.CORD_NEGATIVE, socket);
        InWorldNode b0 = new InWorldNode(StripGeometry.INPUT, socket), b1 = new InWorldNode(StripGeometry.INPUT + 1, socket);
        double len = CordReach.laidLength(d, StripGeometry.CORD_LENGTH);
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        sd.connect(a0, b0, WireData.ofLength(type(), len));
        sd.connect(a1, b1, WireData.ofLength(type(), len));
        reaches.put(key(a0, b0), StripGeometry.CORD_LENGTH);
        SocketPlugs.plugTowards(level, socket, from);
        PowerStripEntity e = stripEntity(strip.id);
        if (e != null)
            e.setSocket(strip.socket);
        setDirty();
        click(here);
        if (p != null)
            message(p, "voltworks.power_strip.plugged_in", CableReelBlockEntity.fmt(StripGeometry.CORD_LENGTH));
        return true;
    }

    private void dropOutlet(BlockPos socket, int supply) {
        Vec3 at = plugWorld(level, socket, supply);
        for (Cord c : new ArrayList<>(cords.values()))
            for (int i = 0; i < 2; i++) {
                End e = c.ends[i];
                if (e.state == State.PLUGGED && e.pos.equals(socket) && e.supply == supply)
                    lay(c, i, at, Vec3.ZERO);
            }
        InWorldNodeConnection strand = cordAt(level, socket, supply);
        if (strand != null) {
            Cord c = unlay(strand, socket, supply);
            lay(c, 1, at, Vec3.ZERO);
        }
    }

    private Cord newStripCord(Strip strip) {
        Cord c = new Cord(UUID.randomUUID());
        c.kind = Kind.STRIP;
        c.reach = StripGeometry.CORD_LENGTH;
        c.ends[0] = End.strip(strip.id);
        cords.put(c.id, c);
        return c;
    }

    private void detachStrip(Strip strip, @Nullable ServerPlayer give, boolean stowPlug) {
        BlockPos socket = strip.socket;
        if (socket == null)
            return;
        for (int supply : StripGeometry.OUTLETS)
            dropOutlet(socket, supply);
        Vec3 at = plugWorld(level, socket, SocketBlock.CORD_POSITIVE);
        InWorldNodeConnection own = cordAt(level, socket, StripGeometry.INPUT);
        if (own != null) {
            reaches.remove(key(own.node1(), own.node2()));
            removePair(own);
        }
        strip.socket = null;
        StripLinks.remove(level, socket, strip.link);
        PowerStripEntity e = stripEntity(strip.id);
        if (e != null)
            e.setSocket(null);
        plugOut(socket);
        if (!stowPlug) {
            Cord c = newStripCord(strip);
            if (give != null)
                giveEnd(c, 1, give);
            else
                lay(c, 1, at, Vec3.ZERO);
            refresh(c);
        }
        setDirty();
    }

    private void stretchOutlets(Strip strip) {
        BlockPos socket = strip.socket;
        if (socket == null)
            return;
        for (int supply : StripGeometry.OUTLETS) {
            InWorldNodeConnection strand = cordAt(level, socket, supply);
            if (strand == null)
                continue;
            InWorldNode other = far(strand, socket, supply);
            Vec3 here = plugWorld(level, socket, supply);
            Vec3 there = plugWorld(level, other.sourcePos(), other.id());
            Double reach = reaches.get(key(strand.node1(), strand.node2()));
            WireData data = InfrastructureSavedData.load(level).getConnectionData(strand);
            double r = CordReach.reachOf(reach, data == null ? 1 : data.length);
            if (WallSpace.distance(level, here, there) > r + STRETCH) {
                Cord c = unlay(strand, socket, supply);
                lay(c, 1, here, Vec3.ZERO);
                level.playSound(null, BlockPos.containing(here), SoundEvents.LEASH_KNOT_BREAK, SoundSource.BLOCKS, 0.6f, 1.3f);
            }
        }
    }

    public @Nullable Vec3 stripTick(PowerStripEntity e) {
        Strip strip = strip(e);
        if (strip.at.distanceToSqr(e.position()) > 1.0E-8 || Math.abs(strip.yaw - e.yaw()) > 1.0E-6) {
            strip.at = e.position();
            strip.yaw = e.yaw();
            setDirty();
        }
        if (strip.socket != null && e.tickCount % 10 == 0) {
            if (level.isLoaded(strip.socket) && (!(level.getBlockState(strip.socket).getBlock() instanceof SocketBlock)
                    || cordAt(level, strip.socket, StripGeometry.INPUT) == null))
                detachStrip(strip, null, false);
            else
                stretchOutlets(strip);
        }
        if (!Objects.equals(e.socket(), strip.socket))
            e.setSocket(strip.socket);
        if (strip.socket != null)
            return plugWorld(level, strip.socket, SocketBlock.CORD_POSITIVE);
        Cord c = stripCordOf(strip);
        return c == null ? null : where(c.ends[1]);
    }

    private void stripLost(Strip strip, boolean drop) {
        detachStrip(strip, null, true);
        Cord c = stripCordOf(strip);
        if (c != null)
            stow(c);
        strips.remove(strip.id);
        if (drop)
            dropStrip(strip.at);
        setDirty();
    }

    private void dropStrip(Vec3 at) {
        ItemEntity item = new ItemEntity(level, at.x, at.y + 0.1, at.z, new ItemStack(VoltworksItems.POWER_STRIP_ITEM.get()));
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    public void stripGone(PowerStripEntity e, boolean drop) {
        if (quietStrips.remove(e.getUUID()))
            return;
        Strip strip = strips.get(e.getUUID());
        if (strip != null)
            stripLost(strip, drop);
    }

    public void pickUpStrip(ServerPlayer p, PowerStripEntity e) {
        Strip strip = strip(e);
        stripLost(strip, false);
        quietStrips.add(e.getUUID());
        e.discard();
        ItemStack stack = new ItemStack(VoltworksItems.POWER_STRIP_ITEM.get());
        if (!p.getInventory().add(stack))
            p.drop(stack, false);
        level.playSound(null, e.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 0.8f);
    }

    public boolean plugHeldStrip(ServerPlayer p, ItemStack stack, PowerStripEntity e, int supply) {
        Cord c = heldCord(stack, p);
        if (c == null)
            return true;
        Strip strip = strip(e);
        if (c.kind == Kind.STRIP && c.ends[0].state == State.STRIP && strip.id.equals(c.ends[0].who)) {
            stow(c);
            level.playSound(null, e.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.BLOCKS, 0.8f, 1.2f);
            message(p, "voltworks.power_strip.stowed");
            return true;
        }
        if (c.kind == Kind.STRIP) {
            deny(p, "voltworks.power_strip.no_chain");
            return false;
        }
        if (strip.socket == null) {
            deny(p, "voltworks.power_strip.no_power");
            return false;
        }
        return plugInto(p, c, heldEnd(stack), strip.socket, supply);
    }

    public boolean useStripOutlet(ServerPlayer p, PowerStripEntity e, int supply, boolean cutters) {
        Strip strip = strip(e);
        if (strip.socket == null || supply < 0)
            return false;
        if (cordAt(level, strip.socket, supply) == null && !isAnchor(strip.socket, supply))
            return false;
        return cutters ? cutAt(level, strip.socket, supply, p) : useOutlet(level, strip.socket, supply, p);
    }

    public void stowStrip(ServerPlayer p, PowerStripEntity e) {
        Strip strip = strip(e);
        Cord c = stripCordOf(strip);
        if (strip.socket == null && c == null)
            return;
        detachStrip(strip, null, true);
        if (c != null)
            stow(c);
        level.playSound(null, e.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.BLOCKS, 0.8f, 1.2f);
        message(p, "voltworks.power_strip.stowed");
    }

    public boolean pullPlug(ServerPlayer p, PowerStripEntity e) {
        Strip strip = strip(e);
        if (strip.socket != null || stripCordOf(strip) != null || !p.getMainHandItem().isEmpty())
            return false;
        Cord c = newStripCord(strip);
        giveEnd(c, 1, p);
        refresh(c);
        level.playSound(null, e.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.BLOCKS, 0.7f, 1.1f);
        message(p, "voltworks.power_strip.grabbed", CableReelBlockEntity.fmt(c.reach));
        return true;
    }

    public void placeStrip(PowerStripEntity e, @Nullable BlockPos socket, @Nullable ServerPlayer p) {
        addStrip(e);
        if (socket != null)
            attachStrip(p, null, strips.get(e.getUUID()), socket);
    }

    public static void chunkLoaded(ServerLevel sl, ChunkAccess chunk) {
        Block block = VoltworksBlocks.POWER_STRIP.get();
        LevelChunkSection[] sections = chunk.getSections();
        List<BlockPos> found = new ArrayList<>();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(st -> st.is(block)))
                continue;
            int y0 = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++)
                        if (section.getBlockState(x, y, z).is(block))
                            found.add(chunk.getPos().getBlockAt(x, y0 + y, z));
        }
        if (!found.isEmpty())
            get(sl).legacy.addAll(found);
    }

    private void convertQueued() {
        if (legacy.isEmpty())
            return;
        for (BlockPos pos : new ArrayList<>(legacy)) {
            legacy.remove(pos);
            if (level.isLoaded(pos))
                convertLegacy(level, pos);
        }
    }

    public void convertLegacy(ServerLevel sl, BlockPos pos) {
        BlockState st = sl.getBlockState(pos);
        if (!(st.getBlock() instanceof PowerStripBlock))
            return;
        BlockPos socket = null;
        InWorldNodeConnection laid = cordAt(sl, pos, PowerStripBlock.INPUT);
        if (laid != null) {
            InWorldNode other = far(laid, pos, PowerStripBlock.INPUT);
            if (other.id() == SocketBlock.CORD_POSITIVE && sl.getBlockState(other.sourcePos()).getBlock() instanceof SocketBlock)
                socket = other.sourcePos();
        }
        blockBroken(sl, pos);
        sl.removeBlock(pos, false);
        double y = switch (st.getValue(PowerStripBlock.FACE)) {
            case FLOOR -> pos.getY();
            case CEILING -> pos.getY() + 0.75;
            default -> pos.getY() + 0.375;
        };
        PowerStripEntity e = new PowerStripEntity(sl, new Vec3(pos.getX() + 0.5, y, pos.getZ() + 0.5), PowerStripBlock.yaw(st));
        if (!sl.addFreshEntity(e)) {
            dropStrip(Vec3.atCenterOf(pos));
            return;
        }
        Voltworks.LOGGER.info("[voltworks] power strip block at {} converted to an entity", pos);
        placeStrip(e, socket, null);
    }

    public static void tick(ServerLevel sl) {
        LooseCords d = get(sl);
        d.convertQueued();
        if (sl.getGameTime() % CHECK_TICKS != 7)
            return;
        if (!d.cords.isEmpty() || !d.strips.isEmpty())
            d.check();
    }

    private void check() {
        for (Strip strip : new ArrayList<>(strips.values())) {
            if (!strips.containsKey(strip.id))
                continue;
            if (stripEntity(strip.id) == null && level.areEntitiesLoaded(ChunkPos.asLong(BlockPos.containing(strip.at)))) {
                stripLost(strip, true);
                continue;
            }
            if (strip.socket != null && level.isLoaded(strip.socket) && (!(level.getBlockState(strip.socket).getBlock() instanceof SocketBlock)
                    || cordAt(level, strip.socket, StripGeometry.INPUT) == null))
                detachStrip(strip, null, false);
        }
        for (Cord c : new ArrayList<>(cords.values())) {
            if (!cords.containsKey(c.id))
                continue;
            if (c.kind == Kind.STRIP) {
                End s = c.ends[0];
                boolean gone = s.state == State.STRIP ? s.who == null || !strips.containsKey(s.who)
                        : level.isLoaded(s.pos) && !(level.getBlockState(s.pos).getBlock() instanceof PowerStripBlock);
                if (gone) {
                    stow(c);
                    continue;
                }
            }
            for (int i = 0; i < 2 && cords.containsKey(c.id); i++) {
                End e = c.ends[i];
                switch (e.state) {
                    case PLUGGED -> {
                        if (c.kind == Kind.STRIP && i == 0)
                            continue;
                        if (level.isLoaded(e.pos) && (!Outlets.isContact(level.getBlockState(e.pos), e.supply)
                                || StripGeometry.isOutlet(e.supply) && stripAt(e.pos) == null))
                            lay(c, i, Vec3.atCenterOf(e.pos), Vec3.ZERO);
                    }
                    case HELD -> {
                        ServerPlayer p = player(e.who);
                        if (p == null)
                            continue;
                        if (p.level() != level) {
                            Vec3 o = where(c.ends[1 - i]);
                            lay(c, i, o != null ? o : Vec3.atCenterOf(e.pos), Vec3.ZERO);
                        } else if (heldCord(p.getMainHandItem(), p) != c) {
                            lay(c, i, p.position().add(0, 0.5, 0), Vec3.ZERO);
                        } else {
                            e.pos = p.blockPosition();
                        }
                    }
                    case LYING -> {
                        if (entity(e.who) == null && level.areEntitiesLoaded(ChunkPos.asLong(e.pos))) {
                            if (c.kind == Kind.STRIP)
                                stow(c);
                            else
                                cut(c);
                        }
                    }
                }
            }
        }
    }

    public static boolean keepsPlug(ServerLevel sl, BlockPos pos) {
        return get(sl).isAnchor(pos, SocketBlock.CORD_POSITIVE);
    }
}
