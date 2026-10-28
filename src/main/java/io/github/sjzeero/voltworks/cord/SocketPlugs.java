package io.github.sjzeero.voltworks.cord;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.simibubi.create.AllSoundEvents;
import io.github.sjzeero.voltworks.reel.CableReelBlockEntity;
import io.github.sjzeero.voltworks.wallwire.WallCell;
import io.github.sjzeero.voltworks.wallwire.WallRoute;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import io.github.sjzeero.voltworks.wallwire.WallWireData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The plug of a copper cord sitting in a socket: the same {@link SocketBlock#PLUG} state (and so the same 3D plug model,
 * with the cord contacts at its back) for a cord reel and for both ends of a socket-to-socket cord. Plugs are put in
 * when a cord is laid, come out when its last strand goes away (checked at the end of the tick), and a slow sweep
 * fixes sockets of cords laid before plugs existed.
 */
public final class SocketPlugs {
    private SocketPlugs() {}

    private static final Map<ServerLevel, Set<BlockPos>> PENDING = new WeakHashMap<>();
    private static final int SWEEP_TICKS = 100;

    /** Turns the plug in the socket at {@code socket} so its cord leaves towards {@code towards} (world space). */
    public static void plugTowards(ServerLevel sl, BlockPos socket, Vec3 towards) {
        BlockState st = sl.getBlockState(socket);
        if (!(st.getBlock() instanceof SocketBlock))
            return;
        SocketBlock.Plug side = side(WallSpace.toWorld(sl, Vec3.atCenterOf(socket)),
                axisWorld(sl, socket, st), towards);
        if (st.getValue(SocketBlock.PLUG) != side)
            sl.setBlock(socket, st.setValue(SocketBlock.PLUG, side), Block.UPDATE_ALL);
    }

    /** World-space direction of a plug in A orientation (Sable sub-levels turned along). */
    private static Vec3 axisWorld(ServerLevel sl, BlockPos socket, BlockState st) {
        Vector3f axis = SocketBlock.plugAxis(st);
        Vec3 centre = Vec3.atCenterOf(socket);
        return WallSpace.toWorld(sl, centre.add(axis.x, axis.y, axis.z)).subtract(WallSpace.toWorld(sl, centre));
    }

    /** A when the cord's way out ({@code towards}) is on the plug axis' side of the socket, B otherwise. */
    public static SocketBlock.Plug side(Vec3 centre, Vec3 axis, Vec3 towards) {
        return axis.dot(towards.subtract(centre)) >= 0 ? SocketBlock.Plug.A : SocketBlock.Plug.B;
    }

    /** Both sockets of a freshly laid cord: towards the other socket (hanging) or the end of the wall route next to each. */
    public static void plugCord(ServerLevel sl, InWorldNode a, InWorldNode b, @Nullable List<WallCell> cellsFromA) {
        Vec3 pa = WallSpace.toWorld(sl, Vec3.atCenterOf(a.sourcePos())), pb = WallSpace.toWorld(sl, Vec3.atCenterOf(b.sourcePos()));
        Vec3 ta = pb, tb = pa;
        if (cellsFromA != null && !cellsFromA.isEmpty()) {
            ta = WallSpace.toWorld(sl, cellsFromA.getFirst().point());
            tb = WallSpace.toWorld(sl, cellsFromA.getLast().point());
        }
        plugTowards(sl, a.sourcePos(), ta);
        plugTowards(sl, b.sourcePos(), tb);
    }

    /** Hook for every removed connection: a socket losing a cord strand is checked at the end of the tick. */
    public static void removed(ServerLevel sl, InWorldNodeConnection c) {
        for (InWorldNode n : new InWorldNode[]{c.node1(), c.node2()})
            if (n.id() == SocketBlock.CORD_POSITIVE || n.id() == SocketBlock.CORD_NEGATIVE)
                PENDING.computeIfAbsent(sl, k -> new LinkedHashSet<>()).add(n.sourcePos().immutable());
    }

    public static void tick(ServerLevel sl) {
        Set<BlockPos> pending = PENDING.remove(sl);
        if (pending != null)
            for (BlockPos p : pending)
                check(sl, p);
        if (sl.getGameTime() % SWEEP_TICKS == 0)
            sweep(sl);
    }

    /** Plug out when nothing is plugged into the cord contacts any more. */
    private static void check(ServerLevel sl, BlockPos p) {
        if (!sl.isLoaded(p))
            return;
        BlockState st = sl.getBlockState(p);
        if (!(st.getBlock() instanceof SocketBlock) || st.getValue(SocketBlock.PLUG) == SocketBlock.Plug.NONE)
            return;
        InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
        if (cordConnections(sd, p).isEmpty())
            sl.setBlock(p, st.setValue(SocketBlock.PLUG, SocketBlock.Plug.NONE), Block.UPDATE_ALL);
    }

    /** Socket-to-socket cords laid before this version: give their sockets a plug too. */
    private static void sweep(ServerLevel sl) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
        List<InWorldNode> nodes = new ArrayList<>();
        for (InWorldNode n : sd.getNodes())
            if (n.id() == SocketBlock.CORD_POSITIVE)
                nodes.add(n);
        for (InWorldNode n : nodes) {
            BlockPos p = n.sourcePos();
            if (!sl.isLoaded(p))
                continue;
            BlockState st = sl.getBlockState(p);
            if (!(st.getBlock() instanceof SocketBlock))
                continue;
            if (st.getValue(SocketBlock.PLUG) != SocketBlock.Plug.NONE) {
                check(sl, p);
                continue;
            }
            InWorldNodeConnection c = socketCord(sl, sd, p);
            if (c == null)
                continue;
            InWorldNode self = self(c, p), other = c.node1().equals(self) ? c.node2() : c.node1();
            WallRoute route = WallWireData.get(sl).get(c);
            Vec3 towards;
            if (route != null && !route.cells.isEmpty()) {
                WallCell first = route.cells.getFirst(), last = route.cells.getLast();
                Vec3 centre = Vec3.atCenterOf(p);
                towards = WallSpace.toWorld(sl, (first.point().distanceToSqr(centre) <= last.point().distanceToSqr(centre) ? first : last).point());
            } else {
                towards = WallSpace.toWorld(sl, Vec3.atCenterOf(other.sourcePos()));
            }
            plugTowards(sl, p, towards);
        }
    }

    private static List<InWorldNodeConnection> cordConnections(InfrastructureSavedData sd, BlockPos p) {
        List<InWorldNodeConnection> out = new ArrayList<>();
        out.addAll(sd.getConnections(new InWorldNode(SocketBlock.CORD_POSITIVE, p)));
        out.addAll(sd.getConnections(new InWorldNode(SocketBlock.CORD_NEGATIVE, p)));
        return out;
    }

    private static InWorldNode self(InWorldNodeConnection c, BlockPos p) {
        return c.node1().sourcePos().equals(p) && (c.node1().id() == SocketBlock.CORD_POSITIVE || c.node1().id() == SocketBlock.CORD_NEGATIVE)
                ? c.node1() : c.node2();
    }

    /** The supply strand of a copper cord between this socket and another socket (not a reel's), or null. */
    public static @Nullable InWorldNodeConnection socketCord(ServerLevel sl, InfrastructureSavedData sd, BlockPos p) {
        if (CableReelBlockEntity.pluggedInto(sl, p) != null)
            return null;
        for (InWorldNodeConnection c : cordConnections(sd, p)) {
            WireData d = sd.getConnectionData(c);
            if (d == null || !CordPair.isCord(d.wireType()))
                continue;
            return CordPair.isReturn(c) ? CordPair.sibling(c) : c;
        }
        return null;
    }

    /**
     * Right-click on a socket holding one end of a socket-to-socket cord: the whole cord comes off (both strands, so
     * the plug at the other end comes out too) and goes back to the player as the wire it was laid from, the same
     * items breaking it would drop.
     */
    public static boolean unplugByPlayer(ServerLevel sl, BlockPos socket, ServerPlayer player) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
        InWorldNodeConnection c = socketCord(sl, sd, socket);
        if (c == null)
            return false;
        WireData data = sd.getConnectionData(c);
        InWorldNodeConnection ret = CordPair.sibling(c);
        WireData retData = sd.getConnectionData(ret);
        if (retData != null && CordPair.isCord(retData.wireType()))
            sd.removeConnectionNoDrops(ret);
        if (data != null)
            sd.removeConnectionNoDrops(c);
        if (data != null && !player.isCreative())
            for (ItemStack stack : drops(data))
                player.getInventory().placeItemBackInInventory(stack);
        removed(sl, c);
        removed(sl, ret);
        unplugNow(sl, c.node1().sourcePos());
        unplugNow(sl, c.node2().sourcePos());
        AllSoundEvents.WRENCH_REMOVE.playOnServer(sl, BlockPos.containing(WallSpace.toWorld(sl, Vec3.atCenterOf(socket))));
        player.displayClientMessage(Component.translatable("voltworks.cord.unplugged"), true);
        return true;
    }

    /** What a cord gives back: one spool with alternate placement, else a spool's worth of wire, like Electro Energetics. */
    private static List<ItemStack> drops(WireData data) {
        List<ItemStack> out = new ArrayList<>();
        if (CEEConfigs.server().alternateWirePlacement.get()) {
            out.add(new ItemStack(data.wireType().getSpooledItem()));
        } else {
            int n = CEEConfigs.server().wiresPerSpool.get();
            for (int i = 0; i < n; i++)
                out.add(new ItemStack(data.wireType().getDrops()));
        }
        return out;
    }

    private static void unplugNow(ServerLevel sl, BlockPos p) {
        if (!sl.isLoaded(p))
            return;
        BlockState st = sl.getBlockState(p);
        if (st.getBlock() instanceof SocketBlock && st.getValue(SocketBlock.PLUG) != SocketBlock.Plug.NONE
                && cordConnections(InfrastructureSavedData.load(sl), p).isEmpty())
            sl.setBlock(p, st.setValue(SocketBlock.PLUG, SocketBlock.Plug.NONE), Block.UPDATE_ALL);
    }
}
