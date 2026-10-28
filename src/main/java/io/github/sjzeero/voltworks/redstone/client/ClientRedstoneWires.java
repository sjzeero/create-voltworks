package io.github.sjzeero.voltworks.redstone.client;

import io.github.sjzeero.voltworks.redstone.RedstoneLinks;
import io.github.sjzeero.voltworks.redstone.RedstoneWiresPayload;
import io.github.sjzeero.voltworks.wallwire.WallCell;
import io.github.sjzeero.voltworks.wallwire.WallRoute;
import net.minecraft.world.phys.AABB;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientRedstoneWires {
    public record Entry(List<WallCell> cells, List<WallRoute.Segment> segments, AABB bounds) {
    }

    private static final Map<RedstoneLinks.Key, Entry> WIRES = new HashMap<>();

    private ClientRedstoneWires() {
    }

    public static void apply(RedstoneWiresPayload payload) {
        if(payload.reset())
            WIRES.clear();
        for(RedstoneLinks.Key key : payload.removed())
            WIRES.remove(key);
        for(List<WallCell> cells : payload.added()) {
            if(cells.size() < 2)
                continue;
            AABB bb = null;
            for(WallCell cell : cells) {
                AABB box = new AABB(cell.point(), cell.point());
                bb = bb == null ? box : bb.minmax(box);
            }
            WallCell a = cells.getFirst(), b = cells.getLast();
            WIRES.put(new RedstoneLinks.Key(a.pos(), a.face(), b.pos(), b.face()),
                    new Entry(List.copyOf(cells), WallRoute.segments(cells), bb.inflate(0.5)));
        }
    }

    public static void clear() {
        WIRES.clear();
    }

    public static Collection<Entry> all() {
        return WIRES.values();
    }
}
