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
import io.github.sjzeero.voltworks.registry.VoltworksWireTypes;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cords with at least one end out of an outlet. A cord plugged in at both ends is an ordinary Electro Energetics
 * connection pair (see {@link CordPair}); pull one plug out and the connection goes away and the cord lives here
 * instead, with the plug still in the other outlet, the free end in the player's hand ({@link ReelPlugItem} stack) or
 * lying on the floor ({@link CableEndEntity}) - the same loose-end system as a cable reel's. Pull the other plug too
 * and the player carries the whole cord: one end in the hand, the other dragged along on the floor, never further
 * than the cord is long. Plug both ends back in, one after the other, and the connection is made again.
 * <p>
 * A power strip's own cord is the same, with its strip end fixed in the strip ({@link Kind#STRIP}); "stowed" (put away
 * in the strip) means neither a connection nor an entry here. Wire cutters take a cord away for good, no items.
 * <p>
 * Saved with the level; held ends are checked against the holder's main hand, lying ends against their entity.
 */
public final class LooseCords extends SavedData {
    private static final String NAME = "voltworks_loose_cords";
    private static final int CHECK_TICKS = 20;

    public enum Kind {CORD, STRIP}

    public enum State {PLUGGED, HELD, LYING}

    public static final class End {
        State state;
        /** PLUGGED: the outlet; LYING: last block the entity was seen in. */
        BlockPos pos = BlockPos.ZERO;
        int supply;
        /** HELD: the player; LYING: the entity. */
        @Nullable UUID who;
        long grab;

        static End plugged(BlockPos pos, int supply) {
            End e = new End();
            e.state = State.PLUGGED;
            e.pos = pos.immutable();
            e.supply = supply;
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

    private final ServerLevel level;
    private final Map<UUID, Cord> cords = new LinkedHashMap<>();
    /** Length each cord was made with, by the key of its supply connection, while it is plugged in at both ends. */
    private final Map<String, Double> reaches = new HashMap<>();
    /** Lying ends being taken away on purpose: their removal is not a lost cord. */
    private final Set<UUID> quiet = new HashSet<>();

    private LooseCords(ServerLevel level) {
        this.level = level;
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
        return tag;
    }

    // ---- small helpers -----------------------------------------------------------------------------------------

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

    /** World position of the plug in an outlet (the back of the plug, where the cord leaves it). */
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

    /** Where an end is now (world space), or null when unknown (holder offline, entity unloaded). */
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
        };
    }

    /** Is the outlet {@code supply} of the block at {@code pos} holding the plug of a cord whose other end is out? */
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
            if (c.kind == Kind.STRIP && c.ends[0].pos.equals(strip))
                return c;
        return null;
    }

    /** No plug in this outlet: no cord or reel connection on its contacts, no loose cord's plug. */
    public boolean free(BlockPos pos, int supply) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        return sd.getConnections(new InWorldNode(supply, pos)).isEmpty() && sd.getConnections(new InWorldNode(supply + 1, pos)).isEmpty()
                && !isAnchor(pos, supply);
    }

    /** The supply strand of a cord (not a reel's) plugged into this outlet, or into the strip's own cord end. */
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

    /** Takes the plug out of a socket when nothing holds it in any more (strip outlets draw their plugs from the cords). */
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

    // ---- the held end ------------------------------------------------------------------------------------------

    public static boolean isLooseCord(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains("LooseCord");
    }

    private static @Nullable CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || !data.contains("LooseCord") ? null : data.copyTag();
    }

    /** What the client needs to draw the cord from the hand to its other end. */
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
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        return stack;
    }

    /** The cord and end a held stack stands for, while it still does. */
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

    /** Puts the end down as a lying end at {@code at} (no further than the cord reaches from its other end). */
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

    /** Pushes where each end's other end is to its entity / held stack. */
    private void refresh(Cord c) {
        for (int i = 0; i < 2; i++) {
            End e = c.ends[i], o = c.ends[1 - i];
            if (e.state == State.LYING) {
                CableEndEntity ent = entity(e.who);
                if (ent != null) {
                    ServerPlayer holder = o.state == State.HELD ? player(o.who) : null;
                    ent.setOther(o.state == State.PLUGGED ? o.pos : BlockPos.ZERO, o.state == State.PLUGGED ? o.supply : -1,
                            holder != null ? holder.getId() : -1, (float) c.reach);
                }
            } else if (e.state == State.HELD) {
                ServerPlayer p = player(e.who);
                if (p != null && heldCord(p.getMainHandItem(), p) == c && heldEnd(p.getMainHandItem()) == i)
                    p.setItemInHand(InteractionHand.MAIN_HAND, heldStack(c, i));
            }
        }
    }

    /** Takes a free end out of the world: the held stack out of the hand, the lying entity away. */
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

    // ---- unplugging ------------------------------------------------------------------------------------------------

    /**
     * Right-click with an empty hand (or anything that is not a block or a tool) on an outlet: the plug in it comes
     * out. A reel's cord comes out like before; one end of a laid cord comes out into the hand (or falls in front of the
     * outlet when the hand is full) while its other plug stays in; the last plug of a cord already out at one end
     * comes out too and the player carries the whole cord. False when no plug is in there.
     */
    public boolean useOutlet(ServerLevel sl, BlockPos pos, int supply, ServerPlayer p) {
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

    /** Frees end {@code i}: into the empty main hand, else onto the floor at {@code at}. */
    private void free(Cord c, int i, @Nullable ServerPlayer p, Vec3 at) {
        if (p != null && p.getMainHandItem().isEmpty() && c.ends[1 - i].state != State.HELD)
            giveEnd(c, i, p);
        else
            lay(c, i, at, Vec3.ZERO);
        refresh(c);
    }

    /**
     * Turns the laid cord whose supply strand is {@code strand} into a loose cord plugged in only at its far end
     * (index 0); end 1 (the one at {@code pos}) is left for the caller to set.
     */
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
            c.reach = PowerStripBlock.CORD_LENGTH;
        } else {
            c.kind = Kind.CORD;
            c.reach = CordReach.reachOf(remembered, laid);
        }
        c.ends[0] = End.plugged(other.sourcePos(), other.id() & ~1);
        c.ends[1] = End.plugged(pos, supply); // placeholder until the caller frees it
        cords.put(c.id, c);
        setDirty();
        return c;
    }

    // ---- plugging in -----------------------------------------------------------------------------------------------

    /**
     * The held end clicked on a block: into the outlet under the click. With the other end in, the connection is made
     * (no longer than the cord); with the other end out too, this end just stays in that outlet. Clicking the outlet
     * the other end is in takes that one out too (carrying the cord); clicking a power strip with its own cord puts the
     * cord back in. True when the held stack is used up.
     */
    public boolean plugHeld(ServerPlayer p, ItemStack stack, BlockPos pos, Vec3 click) {
        Cord c = heldCord(stack, p);
        if (c == null)
            return true;
        int end = heldEnd(stack);
        End o = c.ends[1 - end];
        if (c.kind == Kind.STRIP && pos.equals(c.ends[0].pos)) {
            stow(c);
            level.playSound(null, BlockPos.containing(WallSpace.toWorld(level, Vec3.atCenterOf(pos))), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.BLOCKS, 0.8f, 1.2f);
            message(p, "voltworks.power_strip.stowed");
            return true;
        }
        BlockState state = level.getBlockState(pos);
        int supply = Outlets.pick(level, pos, click);
        if (supply < 0) {
            deny(p, "voltworks.cord.needs_outlet");
            return false;
        }
        if (o.state == State.PLUGGED && o.pos.equals(pos) && o.supply == supply) {
            // its own other plug: that one comes out as well, the cord is carried
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
            InWorldNode a0 = c.kind == Kind.STRIP ? new InWorldNode(StripGeometry.INPUT, o.pos) : new InWorldNode(o.supply, o.pos);
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
        // the other end is out too: this one stays in the outlet, the other one lies where it was dropped
        c.ends[end] = End.plugged(pos, supply);
        if (o.state == State.HELD)
            lay(c, 1 - end, p.position().add(0, 0.5, 0), Vec3.ZERO);
        Vec3 otherAt = where(c.ends[1 - end]);
        SocketPlugs.plugTowards(level, pos, otherAt != null ? otherAt : p.position());
        refresh(c);
        setDirty();
        click(here);
        message(p, "voltworks.cord.end_plugged");
        return true;
    }

    /** Sneak-click into the air with the end in the hand: a strip's cord goes back in, any other cord is put down. */
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

    /**
     * Every few ticks for a held end: out of the main hand it falls; past the cord length from its plugged-in other end
     * (plus a little give, warned about first) it slips out of the hand. A free other end is dragged along instead.
     */
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

    /** Dropped or thrown (Q, death): it falls on as a lying end. */
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

    // ---- the lying end -----------------------------------------------------------------------------------------

    /** Is this entity still a lying end of a cord (else it goes away)? Also keeps its other end up to date. */
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
            End o = c.ends[1 - end];
            ServerPlayer holder = o.state == State.HELD ? player(o.who) : null;
            ent.setOther(o.state == State.PLUGGED ? o.pos : BlockPos.ZERO, o.state == State.PLUGGED ? o.supply : -1,
                    holder != null ? holder.getId() : -1, (float) c.reach);
        }
        return true;
    }

    /** Right-click on a lying end: into the empty main hand. */
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

    /** A lying end went away for good (killed, fell out of the world): the cord is lost, a strip's goes back in. */
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

    // ---- cutting and putting away ------------------------------------------------------------------------------

    /** Wire cutters: the cord is gone, no items. A strip's own cord can't be cut off the strip: it goes back in. */
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

    /** The strip's cord back into the strip, wherever its plug is. */
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

    /** Wire cutters on an outlet (or a strip's cord end): the cord plugged in there is cut, no items. */
    public boolean cutAt(ServerLevel sl, BlockPos pos, int supply, ServerPlayer p) {
        Vec3 at = plugWorld(sl, pos, supply);
        boolean stripInput = Outlets.isStripInput(sl.getBlockState(pos), supply);
        if (stripInput) {
            Cord own = stripCord(pos);
            InWorldNodeConnection laid = cordAt(sl, pos, StripGeometry.INPUT);
            if (own == null && laid == null)
                return false;
            if (own != null)
                stow(own);
            if (laid != null) {
                InWorldNode other = far(laid, pos, StripGeometry.INPUT);
                reaches.remove(key(laid.node1(), laid.node2()));
                removePair(laid);
                plugOut(other.sourcePos());
            }
            setDirty();
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

    /** A laid cord (both plugs in) cut anywhere: both strands go, no items; a strip's cord goes back into the strip. */
    public void cutLaid(InWorldNodeConnection strand) {
        InWorldNodeConnection supply = CordPair.isReturn(strand) ? CordPair.sibling(strand) : strand;
        reaches.remove(key(supply.node1(), supply.node2()));
        removePair(supply);
        plugOut(supply.node1().sourcePos());
        plugOut(supply.node2().sourcePos());
        setDirty();
    }

    // ---- the power strip -------------------------------------------------------------------------------------------

    /**
     * Empty hand on the strip (not on an outlet): its own cord's plug comes out into the hand. Sneaking: the cord goes
     * back into the strip, wherever its plug is (in an outlet, in someone's hand, on the floor).
     */
    public void useStrip(ServerLevel sl, BlockPos strip, ServerPlayer p, boolean sneaking) {
        Cord own = stripCord(strip);
        InWorldNodeConnection laid = cordAt(sl, strip, StripGeometry.INPUT);
        if (sneaking) {
            if (own == null && laid == null)
                return;
            if (own != null)
                stow(own);
            if (laid != null) {
                InWorldNode other = far(laid, strip, StripGeometry.INPUT);
                reaches.remove(key(laid.node1(), laid.node2()));
                removePair(laid);
                plugOut(other.sourcePos());
            }
            setDirty();
            sl.playSound(null, BlockPos.containing(WallSpace.toWorld(sl, Vec3.atCenterOf(strip))), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.BLOCKS, 0.8f, 1.2f);
            message(p, "voltworks.power_strip.stowed");
            return;
        }
        if (laid != null) {
            message(p, "voltworks.power_strip.cord_plugged");
            return;
        }
        if (own != null) {
            message(p, "voltworks.power_strip.cord_out");
            return;
        }
        if (!p.getMainHandItem().isEmpty())
            return;
        Cord c = new Cord(UUID.randomUUID());
        c.kind = Kind.STRIP;
        c.reach = PowerStripBlock.CORD_LENGTH;
        c.ends[0] = End.plugged(strip, StripGeometry.INPUT);
        cords.put(c.id, c);
        giveEnd(c, 1, p);
        sl.playSound(null, BlockPos.containing(WallSpace.toWorld(sl, Vec3.atCenterOf(strip))), SoundEvents.FISHING_BOBBER_THROW, SoundSource.BLOCKS, 0.7f, 1.1f);
        message(p, "voltworks.power_strip.grabbed", CableReelBlockEntity.fmt(c.reach));
    }

    /** A strip placed against a socket (or another strip): its cord goes straight into a free outlet there. */
    public void plugStripInto(ServerLevel sl, BlockPos strip, BlockPos target, Vec3 eye, @Nullable ServerPlayer p) {
        if (!sl.isLoaded(target) || target.equals(strip))
            return;
        BlockState ts = sl.getBlockState(target);
        int chosen = -1;
        double best = Double.MAX_VALUE;
        Vec3 from = plugWorld(sl, strip, StripGeometry.INPUT);
        for (int s : Outlets.of(ts)) {
            if (!free(target, s))
                continue;
            double d = WallSpace.distance(sl, from, plugWorld(sl, target, s));
            if (d < best) {
                best = d;
                chosen = s;
            }
        }
        if (chosen < 0 || !CordReach.fits(best, PowerStripBlock.CORD_LENGTH))
            return;
        BlockState ss = sl.getBlockState(strip);
        if (ss.getBlock() instanceof ElectricalDeviceBlock<?> b)
            b.ensureNodesExist(sl, strip, ss);
        if (ts.getBlock() instanceof ElectricalDeviceBlock<?> b)
            b.ensureNodesExist(sl, target, ts);
        InWorldNode a0 = new InWorldNode(StripGeometry.INPUT, strip), a1 = new InWorldNode(StripGeometry.INPUT + 1, strip);
        InWorldNode b0 = new InWorldNode(chosen, target), b1 = new InWorldNode(chosen + 1, target);
        double len = CordReach.laidLength(best, PowerStripBlock.CORD_LENGTH);
        InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
        sd.connect(a0, b0, WireData.ofLength(type(), len));
        sd.connect(a1, b1, WireData.ofLength(type(), len));
        reaches.put(key(a0, b0), PowerStripBlock.CORD_LENGTH);
        SocketPlugs.plugTowards(sl, target, from);
        setDirty();
        if (p != null)
            message(p, "voltworks.power_strip.auto_plugged");
    }

    // ---- blocks going away ---------------------------------------------------------------------------------------

    /**
     * A socket or power strip broken by a player (before Electro Energetics drops its wires): cords plugged in there
     * fall out and lie in front of it, their other plugs stay in; the strip's own cord goes away with the strip.
     */
    public void blockBroken(ServerLevel sl, BlockPos pos) {
        BlockState st = sl.getBlockState(pos);
        // the strip's own cord leaves with the strip, without items
        if (st.getBlock() instanceof PowerStripBlock) {
            Cord own = stripCord(pos);
            if (own != null)
                stow(own);
            InWorldNodeConnection laid = cordAt(sl, pos, StripGeometry.INPUT);
            if (laid != null) {
                InWorldNode other = far(laid, pos, StripGeometry.INPUT);
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
                continue; // the reel's own hook drops its end
            InWorldNodeConnection strand = cordAt(sl, pos, s);
            if (strand != null) {
                Cord c = unlay(strand, pos, s);
                lay(c, 1, at, Vec3.ZERO);
            }
        }
        setDirty();
    }

    /**
     * A laid strip cord removed some other way (its outlet block exploded, a Sable structure tore it): no cord items,
     * the cord is back in the strip. True when the connection was a strip's.
     */
    public static boolean isStripStrand(ServerLevel sl, InWorldNodeConnection c) {
        for (InWorldNode n : new InWorldNode[]{c.node1(), c.node2()})
            if (sl.isLoaded(n.sourcePos()) && Outlets.isStripInput(sl.getBlockState(n.sourcePos()), n.id()))
                return true;
        return false;
    }

    // ---- consistency -----------------------------------------------------------------------------------------------

    public static void tick(ServerLevel sl) {
        if (sl.getGameTime() % CHECK_TICKS != 7)
            return;
        LooseCords d = get(sl);
        if (!d.cords.isEmpty())
            d.check();
    }

    /**
     * Plugs whose outlet is gone fall out; a strip's cord whose strip is gone goes with it; a held end no longer in the
     * holder's hand (online, same level) falls at the holder; a lying end whose entity vanished while its chunk was
     * loaded counts as lost.
     */
    private void check() {
        for (Cord c : new ArrayList<>(cords.values())) {
            if (!cords.containsKey(c.id))
                continue;
            if (c.kind == Kind.STRIP) {
                End s = c.ends[0];
                if (level.isLoaded(s.pos) && !(level.getBlockState(s.pos).getBlock() instanceof PowerStripBlock)) {
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
                        if (level.isLoaded(e.pos) && !Outlets.isContact(level.getBlockState(e.pos), e.supply))
                            lay(c, i, Vec3.atCenterOf(e.pos), Vec3.ZERO);
                    }
                    case HELD -> {
                        ServerPlayer p = player(e.who);
                        if (p == null)
                            continue; // offline: the stack is saved with the player
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

    // ---- client-visible lookups (server side) --------------------------------------------------------------------

    /** Socket plugs that must stay in: the loose cords' plugged ends. */
    public static boolean keepsPlug(ServerLevel sl, BlockPos pos) {
        return get(sl).isAnchor(pos, SocketBlock.CORD_POSITIVE);
    }
}
