package io.github.sjzeero.voltworks.wallwire;

import io.github.sjzeero.voltworks.cord.CordPair;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class WallWireData extends SavedData {
    private static final String NAME = "voltworks_wall_wires";

    private final ServerLevel level;
    private final Map<InWorldNodeConnection, WallRoute> routes = new LinkedHashMap<>();
    private final Map<BlockPos, Set<InWorldNodeConnection>> byPos = new HashMap<>();
    private final Set<InWorldNodeConnection> pendingChecks = new LinkedHashSet<>();
    private final Set<BlockPos> changedBlocks = new LinkedHashSet<>();
    private Iterator<InWorldNodeConnection> sweep = Collections.emptyIterator();

    private WallWireData(ServerLevel level) {
        this.level = level;
    }

    public static WallWireData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(() -> new WallWireData(level),
                (tag, provider) -> load(level, tag)), NAME);
    }

    private static WallWireData load(ServerLevel level, CompoundTag tag) {
        WallWireData data = new WallWireData(level);
        for(Tag t : tag.getList("Routes", Tag.TAG_COMPOUND)) {
            WallRoute route = WallRoute.load((CompoundTag) t);
            if(!route.cells.isEmpty())
                data.index(route);
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        ListTag list = new ListTag();
        for(WallRoute route : routes.values())
            list.add(route.save());
        tag.put("Routes", list);
        return tag;
    }

    public @Nullable WallRoute get(InWorldNodeConnection connection) {
        return routes.get(connection);
    }

    public Collection<WallRoute> all() {
        return routes.values();
    }

    public void put(WallRoute route) {
        WallRoute old = routes.get(route.connection);
        if(old != null)
            unindex(old);
        index(route);
        setDirty();
        WallWireSync.added(level, route);
    }

    public void remove(InWorldNodeConnection connection) {
        WallRoute old = routes.get(connection);
        if(old == null)
            return;
        unindex(old);
        setDirty();
        WallWireSync.removed(level, connection);
    }

    private void index(WallRoute route) {
        routes.put(route.connection, route);
        WallRouter.forEachTouchedPos(route.cells, p -> byPos.computeIfAbsent(p, k -> new HashSet<>()).add(route.connection));
        sweep = Collections.emptyIterator();
    }

    private void unindex(WallRoute route) {
        routes.remove(route.connection);
        WallRouter.forEachTouchedPos(route.cells, p -> {
            Set<InWorldNodeConnection> set = byPos.get(p);
            if(set != null && set.remove(route.connection) && set.isEmpty())
                byPos.remove(p);
        });
        sweep = Collections.emptyIterator();
    }

    public void queueCheck(InWorldNodeConnection connection) {
        if(routes.containsKey(connection))
            pendingChecks.add(connection);
    }

    public void blockChanged(BlockPos pos) {
        if(byPos.containsKey(pos))
            changedBlocks.add(pos.immutable());
    }

    public void tick() {
        if(routes.isEmpty())
            return;
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);

        if(!changedBlocks.isEmpty()) {
            List<BlockPos> changed = new ArrayList<>(changedBlocks);
            changedBlocks.clear();
            Set<InWorldNodeConnection> broken = new LinkedHashSet<>();
            for(BlockPos pos : changed) {
                Set<InWorldNodeConnection> set = byPos.get(pos);
                if(set == null)
                    continue;
                for(InWorldNodeConnection c : set) {
                    WallRoute route = routes.get(c);
                    if(route != null && !stillSupported(route, pos))
                        broken.add(c);
                }
            }
            for(InWorldNodeConnection c : broken)
                breakWire(sd, c);
        }

        if(!sweep.hasNext())
            sweep = new ArrayList<>(routes.keySet()).iterator();
        if(sweep.hasNext()) {
            InWorldNodeConnection c = sweep.next();
            WallRoute route = routes.get(c);
            if(route != null && !stillSupported(route, null))
                breakWire(sd, c);
        }

        if(!pendingChecks.isEmpty()) {
            List<InWorldNodeConnection> checks = new ArrayList<>(pendingChecks);
            pendingChecks.clear();
            for(InWorldNodeConnection c : checks)
                if(!sd.isConnected(c.node1(), c.node2())) {
                    remove(c);
                    InWorldNodeConnection s = CordPair.sibling(c);
                    WireData sw = sd.getConnectionData(s);
                    if(routes.containsKey(s) && sw != null && CordPair.isCord(sw.wireType()))
                        sd.removeConnectionNoDrops(s);
                    if(routes.containsKey(s) && !sd.isConnected(s.node1(), s.node2()))
                        remove(s);
                }
        }
    }

    private void breakWire(InfrastructureSavedData sd, InWorldNodeConnection c) {
        WireData data = sd.getConnectionData(c);
        if(data != null) {
            if(CordPair.isCord(data.wireType())) {
                InWorldNodeConnection s = CordPair.sibling(c);
                WireData sw = sd.getConnectionData(s);
                if(sw != null && CordPair.isCord(sw.wireType())) {
                    sd.removeConnectionNoDrops(s);
                    pendingChecks.add(s);
                }
            }
            sd.removeAndDropConnection(c);
        }
        pendingChecks.add(c);
    }

    private boolean stillSupported(WallRoute route, @Nullable BlockPos pos) {
        for(WallCell cell : route.cells) {
            if(pos != null && !cell.pos().equals(pos) && !cell.front().equals(pos))
                continue;
            if(!level.isLoaded(cell.pos()) || !level.isLoaded(cell.front()))
                continue;
            if(WallShapes.passable(level.getBlockState(cell.pos())) || WallShapes.passable(level.getBlockState(cell.front())))
                continue;
            if(!WallRouter.supports(level, cell) && !WallRouter.supportsFullFace(level, cell))
                return false;
        }
        WallShapes shapes = new WallShapes(level);
        for(int i = 1; i < route.cells.size(); i++) {
            WallCell a = route.cells.get(i - 1), b = route.cells.get(i);
            if(WallRouter.isSpan(a, b) && !WallRouter.forEachSpanPixel(a, b, (x, y, z) -> {
                BlockPos p = new BlockPos(x >> 4, y >> 4, z >> 4);
                return pos != null && !p.equals(pos) || !level.isLoaded(p) || !shapes.solid(x, y, z);
            }))
                return false;
        }
        return true;
    }
}
