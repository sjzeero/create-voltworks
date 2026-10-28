package io.github.sjzeero.voltworks.cord;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import io.github.sjzeero.voltworks.reel.CableReelBlockEntity;
import io.github.sjzeero.voltworks.wallwire.WallCell;
import io.github.sjzeero.voltworks.wallwire.WallRoute;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import io.github.sjzeero.voltworks.wallwire.WallWireData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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

public final class SocketPlugs {
    private SocketPlugs() {}

    private static final Map<ServerLevel, Set<BlockPos>> PENDING = new WeakHashMap<>();
    private static final int SWEEP_TICKS = 100;

    public static void plugTowards(ServerLevel sl, BlockPos socket, Vec3 towards) {
        BlockState st = sl.getBlockState(socket);
        if (!(st.getBlock() instanceof SocketBlock))
            return;
        SocketBlock.Plug side = side(WallSpace.toWorld(sl, Vec3.atCenterOf(socket)),
                axisWorld(sl, socket, st), towards);
        if (st.getValue(SocketBlock.PLUG) != side)
            sl.setBlock(socket, st.setValue(SocketBlock.PLUG, side), Block.UPDATE_ALL);
    }

    private static Vec3 axisWorld(ServerLevel sl, BlockPos socket, BlockState st) {
        Vector3f axis = SocketBlock.plugAxis(st);
        Vec3 centre = Vec3.atCenterOf(socket);
        return WallSpace.toWorld(sl, centre.add(axis.x, axis.y, axis.z)).subtract(WallSpace.toWorld(sl, centre));
    }

    public static SocketBlock.Plug side(Vec3 centre, Vec3 axis, Vec3 towards) {
        return axis.dot(towards.subtract(centre)) >= 0 ? SocketBlock.Plug.A : SocketBlock.Plug.B;
    }

    public static void plugCord(ServerLevel sl, InWorldNode a, InWorldNode b, @Nullable List<WallCell> cellsFromA) {
        Vec3 pa = WallSpace.toWorld(sl, Vec3.atCenterOf(a.sourcePos())), pb = WallSpace.toWorld(sl, Vec3.atCenterOf(b.sourcePos()));
        Vec3 ta = pb, tb = pa;
        if (cellsFromA != null && !cellsFromA.isEmpty()) {
            ta = WallSpace.toWorld(sl, cellsFromA.getFirst().point());
            tb = WallSpace.toWorld(sl, cellsFromA.getLast().point());
        }
        if (!StripGeometry.isStripNode(a.id()))
            plugTowards(sl, a.sourcePos(), ta);
        if (!StripGeometry.isStripNode(b.id()))
            plugTowards(sl, b.sourcePos(), tb);
    }

    public static void removed(ServerLevel sl, InWorldNodeConnection c) {
        for (InWorldNode n : new InWorldNode[]{c.node1(), c.node2()})
            if (n.id() == SocketBlock.CORD_POSITIVE || n.id() == SocketBlock.CORD_NEGATIVE)
                PENDING.computeIfAbsent(sl, k -> new LinkedHashSet<>()).add(n.sourcePos().immutable());
    }

    public static void tick(ServerLevel sl) {
        LooseCords.tick(sl);
        Set<BlockPos> pending = PENDING.remove(sl);
        if (pending != null)
            for (BlockPos p : pending)
                check(sl, p);
        if (sl.getGameTime() % SWEEP_TICKS == 0)
            sweep(sl);
    }

    private static void check(ServerLevel sl, BlockPos p) {
        if (!sl.isLoaded(p))
            return;
        BlockState st = sl.getBlockState(p);
        if (!(st.getBlock() instanceof SocketBlock) || st.getValue(SocketBlock.PLUG) == SocketBlock.Plug.NONE)
            return;
        InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
        if (cordConnections(sd, p).isEmpty() && !LooseCords.keepsPlug(sl, p))
            sl.setBlock(p, st.setValue(SocketBlock.PLUG, SocketBlock.Plug.NONE), Block.UPDATE_ALL);
    }

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
}
