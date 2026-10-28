package io.github.sjzeero.voltworks.cord;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.WireType;
import io.github.sjzeero.voltworks.registry.VoltworksWireTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The copper cord is laid as a Voltworks wall wire carrying two strands over the same cells: a supply strand
 * between the even nodes of both ends and a return strand between the odd ones. It only plugs into outlets
 * (a socket's contacts 2/3, a power strip's 2/3, 4/5, 6/7, see {@link Outlets}); it is not a general purpose wire. Only the supply strand
 * is drawn, as one 2x1 px band; losing either strand removes the other without a second drop.
 */
public final class CordPair {
    private CordPair() {}

    public static boolean isCord(@Nullable WireType type) {
        return type != null && type == VoltworksWireTypes.COPPER_CORD.get();
    }

    /** Supply and return contact of the socket at pos (a power strip's first outlet), or null when it has no outlet. */
    public static InWorldNode @Nullable [] ends(Level level, BlockPos pos) {
        return ends(level, pos, (Vec3) null);
    }

    /** Contacts of the outlet under {@code hit} (a socket's only one, the nearest of a strip's), or null. */
    public static InWorldNode @Nullable [] ends(Level level, BlockPos pos, @Nullable Vec3 hit) {
        int supply = Outlets.pick(level, pos, hit);
        return supply < 0 ? null : new InWorldNode[]{new InWorldNode(supply, pos), new InWorldNode(supply + 1, pos)};
    }

    /** Contacts of the outlet {@code node} belongs to; for any other node of an outlet block, its first outlet. */
    public static InWorldNode @Nullable [] ends(Level level, InWorldNode node) {
        BlockState state = level.getBlockState(node.sourcePos());
        if (Outlets.isContact(state, node.id()))
            return new InWorldNode[]{new InWorldNode(node.id() & ~1, node.sourcePos()), new InWorldNode((node.id() & ~1) + 1, node.sourcePos())};
        return ends(level, node.sourcePos());
    }

    public static boolean isReturn(InWorldNodeConnection c) {
        return (c.node1().id() & 1) == 1;
    }

    public static InWorldNodeConnection sibling(InWorldNodeConnection c) {
        int d = isReturn(c) ? -1 : 1;
        return new InWorldNodeConnection(new InWorldNode(c.node1().id() + d, c.node1().sourcePos()),
                new InWorldNode(c.node2().id() + d, c.node2().sourcePos()));
    }
}
