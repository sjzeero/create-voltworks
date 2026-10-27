package io.github.sjzeero.voltworks.reel;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import net.minecraft.server.level.ServerLevel;

/** Hook for {@code InfrastructureSavedData.removeConnection}: a cable pulled out of a reel winds back instead of dropping. */
public final class CableReels {
    private CableReels() {}

    public static boolean windBack(ServerLevel level, InWorldNodeConnection connection) {
        return windBack(level, connection.node1(), connection) || windBack(level, connection.node2(), connection);
    }

    private static boolean windBack(ServerLevel level, InWorldNode node, InWorldNodeConnection connection) {
        if (!level.isLoaded(node.sourcePos()))
            return false;
        if (!(level.getBlockState(node.sourcePos()).getBlock() instanceof CableReelBlock reel) || !reel.isOutlet(node.id()))
            return false;
        return level.getBlockEntity(node.sourcePos()) instanceof CableReelBlockEntity be && be.windBackFor(connection);
    }
}
