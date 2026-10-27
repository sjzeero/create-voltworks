package io.github.sjzeero.voltworks.reel;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** Hook for {@code InfrastructureSavedData.removeConnection}: a cable pulled out of a reel winds back instead of dropping. */
public final class CableReels {
    private CableReels() {}

    public static boolean windBack(ServerLevel level, InWorldNodeConnection connection) {
        return windBack(level, connection.node1(), connection) || windBack(level, connection.node2(), connection);
    }

    private static boolean windBack(ServerLevel level, InWorldNode node, InWorldNodeConnection connection) {
        CableReelBlockEntity be = reelAt(level, node);
        return be != null && be.windBackFor(connection);
    }

    /** The reel whose hidden outlet this node is, or null. */
    static @Nullable CableReelBlockEntity reelAt(ServerLevel level, InWorldNode node) {
        if (!level.isLoaded(node.sourcePos()))
            return null;
        if (!(level.getBlockState(node.sourcePos()).getBlock() instanceof CableReelBlock reel) || !reel.isOutlet(node.id()))
            return null;
        return level.getBlockEntity(node.sourcePos()) instanceof CableReelBlockEntity be ? be : null;
    }

    /**
     * Hook for {@code WireSimulationState.relocateConnection}: an end of a reel's cable moved (Sable sub-level), so the
     * reel pays out or winds in. True when the cable is stretched past all the wire on the reel and has to come off.
     */
    public static boolean follow(InfrastructureSavedData sd, InWorldNodeConnection connection, WireData data) {
        ServerLevel level = sd.level;
        if (level == null)
            return false;
        for (InWorldNode node : new InWorldNode[]{connection.node1(), connection.node2()}) {
            CableReelBlockEntity be = reelAt(level, node);
            if (be != null)
                return be.follow(sd, connection, node.id(), data);
        }
        return false;
    }
}
