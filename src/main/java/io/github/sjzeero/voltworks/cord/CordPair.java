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

public final class CordPair {
    private CordPair() {}

    public static boolean isCord(@Nullable WireType type) {
        return type != null && type == VoltworksWireTypes.COPPER_CORD.get();
    }

    public static InWorldNode @Nullable [] ends(Level level, BlockPos pos) {
        return ends(level, pos, (Vec3) null);
    }

    public static InWorldNode @Nullable [] ends(Level level, BlockPos pos, @Nullable Vec3 hit) {
        int supply = Outlets.pick(level, pos, hit);
        return supply < 0 ? null : new InWorldNode[]{new InWorldNode(supply, pos), new InWorldNode(supply + 1, pos)};
    }

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
