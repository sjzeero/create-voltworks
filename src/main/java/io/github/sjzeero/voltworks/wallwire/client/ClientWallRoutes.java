package io.github.sjzeero.voltworks.wallwire.client;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import io.github.sjzeero.voltworks.wallwire.WallCell;
import io.github.sjzeero.voltworks.wallwire.WallRoute;
import io.github.sjzeero.voltworks.wallwire.WallRoutesPayload;
import net.createmod.catnip.data.Pair;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientWallRoutes {
    public record Entry(WallRoute route, List<WallRoute.Segment> segments, AABB bounds) {
    }

    private static final Map<InWorldNodeConnection, Entry> ROUTES = new HashMap<>();

    private ClientWallRoutes() {
    }

    public static void apply(WallRoutesPayload payload) {
        if(payload.reset())
            ROUTES.clear();
        for(InWorldNodeConnection c : payload.removed())
            ROUTES.remove(c);
        for(WallRoute route : payload.added()) {
            if(route.cells.isEmpty())
                continue;
            AABB bb = null;
            for(WallCell cell : route.cells) {
                AABB box = new AABB(cell.point(), cell.point());
                bb = bb == null ? box : bb.minmax(box);
            }
            ROUTES.put(route.connection, new Entry(route, route.surfaceSegments(), bb.inflate(0.5)));
        }
    }

    public static void clear() {
        ROUTES.clear();
    }

    public static boolean isWall(InWorldNodeConnection connection) {
        return ROUTES.containsKey(connection);
    }

    public static Entry get(InWorldNodeConnection connection) {
        return ROUTES.get(connection);
    }

    public static Collection<Entry> all() {
        return ROUTES.values();
    }

    public static List<Pair<InWorldNodeConnection, WireData>> withoutWallWires(List<Pair<InWorldNodeConnection, WireData>> wires) {
        if(ROUTES.isEmpty())
            return wires;
        List<Pair<InWorldNodeConnection, WireData>> out = new ArrayList<>(wires.size());
        for(Pair<InWorldNodeConnection, WireData> w : wires)
            if(!ROUTES.containsKey(w.getFirst()))
                out.add(w);
        return out;
    }
}
