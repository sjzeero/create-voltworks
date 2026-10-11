package io.github.sjzeero.voltworks.cord;

import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.WireType;
import io.github.sjzeero.voltworks.registry.VoltworksWireTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The copper cord is laid as a Voltworks wall wire carrying two strands over the same cells: a supply strand
 * between the even nodes of both ends and a return strand between the odd ones. On a socket these are its two
 * contacts (2, 3), on any other device with exactly two terminals its terminals (0, 1). Only the supply strand
 * is drawn, as one 2x1 px band; losing either strand removes the other without a second drop.
 */
public final class CordPair {
    private CordPair() {}

    public static boolean isCord(@Nullable WireType type) {
        return type != null && type == VoltworksWireTypes.COPPER_CORD.get();
    }

    /** Supply and return node a cord plugs into on this block, or null when it takes no cord. */
    public static InWorldNode @Nullable [] ends(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if(state.getBlock() instanceof SocketBlock)
            return new InWorldNode[]{new InWorldNode(SocketBlock.CORD_POSITIVE, pos), new InWorldNode(SocketBlock.CORD_NEGATIVE, pos)};
        if(state.getBlock() instanceof ElectricalDeviceBlock<?> block && block.getNodePositions(level, pos, state).size() == 2)
            return new InWorldNode[]{new InWorldNode(0, pos), new InWorldNode(1, pos)};
        return null;
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
