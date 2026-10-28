package io.github.sjzeero.voltworks.battery;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class BatteryWires {
    private BatteryWires() {}

    static final List<Integer> NODES = List.of(0, 1);

    public static boolean hasWires(ServerLevel level, BlockPos pos) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        for (int id : NODES)
            if (!sd.getConnections(new InWorldNode(id, pos)).isEmpty())
                return true;
        return false;
    }

    public static void move(ServerLevel level, BlockPos from, BlockPos to) {
        if (from.equals(to))
            return;
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        for (int id : NODES) {
            InWorldNode src = new InWorldNode(id, from);
            List<InWorldNodeConnection> wires = sd.getConnections(src);
            if (wires.isEmpty())
                continue;
            InWorldNode dst = new InWorldNode(id, to);
            if (sd.getNodeData(dst) == null)
                sd.registerOrUpdateNodes(to, NODES);
            Vec3 dstPos = sd.getNodePositionOrCenter(dst);
            for (InWorldNodeConnection c : wires) {
                InWorldNode other = c.node1().equals(src) ? c.node2() : c.node1();
                WireData data = sd.getConnectionData(c);
                if (data == null)
                    continue;
                double length = dstPos.distanceTo(sd.getNodePositionOrCenter(other));
                if (other.equals(dst) || sd.isConnected(dst, other) || length > data.wireType().getMaxLength() || length < 1.0E-3) {
                    sd.removeAndDropConnection(c);
                    continue;
                }
                sd.removeConnectionNoDrops(c);
                sd.connect(dst, other, new WireData(data.wireType(), data.temperature(), data.attachments(), length));
            }
        }
    }
}
