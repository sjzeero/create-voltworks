package io.github.sjzeero.voltworks.cord;

import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InWorldNodeData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.simibubi.create.AllSoundEvents;
import io.github.sjzeero.voltworks.registry.VoltworksItems;
import io.github.sjzeero.voltworks.registry.VoltworksWireTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Two-strand cord, ported from Create: Power Grid (patryk3211, Apache-2.0) onto CEE wires.
 * A cord is two {@link VoltworksWireTypes#COPPER_CORD} strands between the same two blocks. They are always
 * removed together and give back exactly one cord item, whatever CEE's wire item settings are.
 */
public final class CordWires {
    public static final int MAX_LENGTH = 16;
    private static boolean removingSibling;

    private CordWires() {}

    public static ItemStack cordStack() {
        return new ItemStack(VoltworksItems.COPPER_CORD.get());
    }

    public static boolean isCord(@Nullable WireData data) {
        return data != null && data.wireType() == VoltworksWireTypes.COPPER_CORD.get();
    }

    /** The two nodes the strands attach to: the socket contacts, or a device's only two terminals. */
    @Nullable
    public static int[] endpointNodes(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof SocketBlock)
            return new int[]{SocketBlock.CORD_POSITIVE, SocketBlock.CORD_NEGATIVE};
        if (!(state.getBlock() instanceof ElectricalDeviceBlock<?> db))
            return null;
        List<Integer> ids = new ArrayList<>();
        for (int id : db.getNodePositions(level, pos, state).keySet())
            if (db.isNodeAccessible(level, pos, state, id))
                ids.add(id);
        if (ids.size() != 2)
            return null;
        Collections.sort(ids);
        return new int[]{ids.get(0), ids.get(1)};
    }

    private static List<InWorldNodeData> nodesAt(InfrastructureSavedData sd, BlockPos pos) {
        List<InWorldNodeData> nodes = sd.getNodesAt(pos);
        return nodes == null ? List.of() : List.copyOf(nodes);
    }

    private static InWorldNode otherEnd(InWorldNodeConnection c, InWorldNode self) {
        return c.node1().equals(self) ? c.node2() : c.node1();
    }

    private static boolean sameConnection(InWorldNodeConnection a, InWorldNodeConnection b) {
        return a.node1().equals(b.node1()) && a.node2().equals(b.node2())
                || a.node1().equals(b.node2()) && a.node2().equals(b.node1());
    }

    /** A cord strand at {@code pos} going to {@code other}, or to anywhere when {@code other} is null. */
    @Nullable
    public static InWorldNodeConnection findStrand(InfrastructureSavedData sd, BlockPos pos, @Nullable BlockPos other, @Nullable InWorldNodeConnection except) {
        for (InWorldNodeData nd : nodesAt(sd, pos)) {
            for (InWorldNodeConnection c : sd.getConnections(nd)) {
                if (except != null && sameConnection(c, except))
                    continue;
                if (other != null && !otherEnd(c, nd.node).sourcePos().equals(other))
                    continue;
                if (isCord(sd.getConnectionData(c)))
                    return c;
            }
        }
        return null;
    }

    public static boolean hasCord(InfrastructureSavedData sd, BlockPos pos) {
        return findStrand(sd, pos, null, null) != null;
    }

    /** Called after CEE removed a connection: take the other strand of the same cord with it. */
    public static void removeSibling(InfrastructureSavedData sd, InWorldNodeConnection removed, @Nullable WireData data) {
        if (removingSibling || !isCord(data))
            return;
        removingSibling = true;
        try {
            InWorldNodeConnection sibling = findStrand(sd, removed.node1().sourcePos(), removed.node2().sourcePos(), removed);
            if (sibling != null)
                sd.removeConnectionNoDrops(sibling);
        } finally {
            removingSibling = false;
        }
    }

    /** Lays both strands. Returns a lang key suffix on failure. */
    @Nullable
    public static String connect(InfrastructureSavedData sd, BlockPos aPos, int[] a, boolean aSocket, BlockPos bPos, int[] b, boolean bSocket) {
        if (aSocket && hasCord(sd, aPos) || bSocket && hasCord(sd, bPos))
            return "socket_taken";
        if (findStrand(sd, aPos, bPos, null) != null)
            return "already_connected";
        InWorldNode a0 = new InWorldNode(a[0], aPos), a1 = new InWorldNode(a[1], aPos);
        InWorldNode b0 = new InWorldNode(b[0], bPos), b1 = new InWorldNode(b[1], bPos);
        if (!sd.hasNode(a0) || !sd.hasNode(a1) || !sd.hasNode(b0) || !sd.hasNode(b1))
            return "not_ready";
        double l0 = sd.getNodePositionOrCenter(a0).distanceTo(sd.getNodePositionOrCenter(b0));
        double l1 = sd.getNodePositionOrCenter(a1).distanceTo(sd.getNodePositionOrCenter(b1));
        if (Math.max(l0, l1) > MAX_LENGTH)
            return "too_far";
        sd.connect(a0, b0, WireData.ofLength(VoltworksWireTypes.COPPER_CORD.get(), l0));
        sd.connect(a1, b1, WireData.ofLength(VoltworksWireTypes.COPPER_CORD.get(), l1));
        return null;
    }

    /** Removes one cord at {@code pos} (both strands). Returns false if there was none. */
    public static boolean unplug(ServerLevel level, BlockPos pos, @Nullable Player player) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        InWorldNodeConnection strand = findStrand(sd, pos, null, null);
        if (strand == null)
            return false;
        Vec3 mid = sd.getNodePositionOrCenter(strand.node1()).add(sd.getNodePositionOrCenter(strand.node2())).scale(0.5);
        sd.removeConnection(strand);
        AllSoundEvents.WRENCH_REMOVE.playOnServer(level, pos);
        give(level, mid, player);
        return true;
    }

    /** Removes every cord at {@code pos}; used before CEE's own wire pickup so it never sees a half-removed cord. */
    public static void removeAll(ServerLevel level, BlockPos pos, @Nullable Player player) {
        while (unplug(level, pos, player)) {
        }
    }

    public static void give(Level level, Vec3 at, @Nullable Player player) {
        if (player != null && player.isCreative())
            return;
        if (player != null)
            player.getInventory().placeItemBackInInventory(cordStack());
        else
            Containers.dropItemStack(level, at.x, at.y, at.z, cordStack());
    }
}
