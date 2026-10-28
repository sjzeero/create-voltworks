package io.github.sjzeero.voltworks.wallwire;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;

public final class SchematicWires {
    public static final String KEY = "voltworks_wall_wires";

    private SchematicWires() {
    }

    private static boolean inside(BlockPos p, BlockPos origin, Vec3i size) {
        return p.getX() >= origin.getX() && p.getX() < origin.getX() + size.getX()
                && p.getY() >= origin.getY() && p.getY() < origin.getY() + size.getY()
                && p.getZ() >= origin.getZ() && p.getZ() < origin.getZ() + size.getZ();
    }

    public static ListTag capture(ServerLevel level, BlockPos origin, Vec3i size) {
        return capture(WallWireData.get(level).all(), origin, size);
    }

    public static ListTag capture(java.util.Collection<WallRoute> routes, BlockPos origin, Vec3i size) {
        ListTag out = new ListTag();
        for(WallRoute route : routes) {
            InWorldNode n1 = route.connection.node1(), n2 = route.connection.node2();
            if(!inside(n1.sourcePos(), origin, size) || !inside(n2.sourcePos(), origin, size))
                continue;
            CompoundTag tag = new CompoundTag();
            tag.putInt("N1Id", n1.id());
            tag.putLong("N1Pos", n1.sourcePos().subtract(origin).asLong());
            tag.putInt("N2Id", n2.id());
            tag.putLong("N2Pos", n2.sourcePos().subtract(origin).asLong());
            long[] pos = new long[route.cells.size()];
            int[] local = new int[pos.length];
            for(int i = 0; i < pos.length; i++) {
                WallCell cell = route.cells.get(i);
                pos[i] = cell.pos().subtract(origin).asLong();
                local[i] = cell.packLocal();
            }
            tag.putLongArray("CPos", pos);
            tag.putIntArray("CLocal", local);
            tag.putDouble("Length", route.length);
            if(route.color != null)
                tag.putString("Color", route.color.getSerializedName());
            out.add(tag);
        }
        return out;
    }

    private static WallCell transform(WallCell rel, StructurePlaceSettings settings, BlockPos origin) {
        BlockPos block = StructureTemplate.calculateRelativePosition(settings, rel.pos()).offset(origin);
        Mirror mirror = settings.getMirror();
        Rotation rotation = settings.getRotation();
        int x = rel.px(), y = rel.py(), z = rel.pz();
        if(mirror == Mirror.LEFT_RIGHT)
            z = 15 - z;
        else if(mirror == Mirror.FRONT_BACK)
            x = 15 - x;
        int nx = x, nz = z;
        switch(rotation) {
            case CLOCKWISE_90 -> {
                nx = 15 - z;
                nz = x;
            }
            case COUNTERCLOCKWISE_90 -> {
                nx = z;
                nz = 15 - x;
            }
            case CLOCKWISE_180 -> {
                nx = 15 - x;
                nz = 15 - z;
            }
            default -> {
            }
        }
        Direction face = rotation.rotate(mirror.mirror(rel.face()));
        return new WallCell(block, face, nx, y, nz);
    }

    private record Parsed(InWorldNode n1, InWorldNode n2, List<WallCell> cells, double length, DyeColor color) {
        WallRoute route() {
            WallRoute route = WallRoute.oriented(n1, n2, cells, length);
            return color == null ? route : route.withColor(color);
        }
    }

    private static List<Parsed> parse(ListTag list, BlockPos origin, StructurePlaceSettings settings) {
        List<Parsed> out = new ArrayList<>();
        for(Tag t : list) {
            CompoundTag tag = (CompoundTag) t;
            long[] pos = tag.getLongArray("CPos");
            int[] local = tag.getIntArray("CLocal");
            if(pos.length == 0 || pos.length != local.length)
                continue;
            InWorldNode n1 = new InWorldNode(tag.getInt("N1Id"), StructureTemplate.calculateRelativePosition(settings, BlockPos.of(tag.getLong("N1Pos"))).offset(origin));
            InWorldNode n2 = new InWorldNode(tag.getInt("N2Id"), StructureTemplate.calculateRelativePosition(settings, BlockPos.of(tag.getLong("N2Pos"))).offset(origin));
            List<WallCell> cells = new ArrayList<>(pos.length);
            for(int i = 0; i < pos.length; i++)
                cells.add(transform(WallCell.unpack(pos[i], local[i]), settings, origin));
            out.add(new Parsed(n1, n2, cells, tag.getDouble("Length"), tag.contains("Color") ? DyeColor.byName(tag.getString("Color"), null) : null));
        }
        return out;
    }

    public static void place(ServerLevel level, ListTag list, BlockPos origin, StructurePlaceSettings settings) {
        List<Parsed> pending = parse(list, origin, settings);
        if(pending.isEmpty())
            return;
        level.getServer().execute(() -> {
            InfrastructureSavedData sd = InfrastructureSavedData.load(level);
            WallWireData walls = WallWireData.get(level);
            for(Parsed p : pending) {
                if(!sd.isConnected(p.n1(), p.n2()))
                    continue;
                walls.put(p.route());
            }
        });
    }

    private static final java.util.Map<Object, List<WallRoute>> PREVIEW = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static void placePreview(Object level, ListTag list, BlockPos origin, StructurePlaceSettings settings) {
        List<WallRoute> out = new ArrayList<>();
        for(Parsed p : parse(list, origin, settings))
            out.add(p.route());
        PREVIEW.put(level, out);
    }

    private static final java.util.Map<ServerLevel, List<WallRoute>> PENDING = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static void addPending(ServerLevel level, ListTag list, BlockPos origin, StructurePlaceSettings settings) {
        List<WallRoute> routes = new ArrayList<>();
        for(Parsed p : parse(list, origin, settings))
            routes.add(p.route());
        if(routes.isEmpty())
            return;
        synchronized(PENDING) {
            PENDING.computeIfAbsent(level, k -> new ArrayList<>()).addAll(routes);
        }
    }

    public static void onConnect(ServerLevel level, InWorldNode a, InWorldNode b) {
        List<WallRoute> list = PENDING.get(level);
        if(list == null)
            return;
        WallRoute found = null;
        synchronized(PENDING) {
            for(WallRoute r : list) {
                InWorldNode n1 = r.connection.node1(), n2 = r.connection.node2();
                if((n1.equals(a) && n2.equals(b)) || (n1.equals(b) && n2.equals(a))) {
                    found = r;
                    break;
                }
            }
            if(found != null)
                list.remove(found);
        }
        if(found != null) {
            WallRoute route = found;
            level.getServer().execute(() -> WallWireData.get(level).put(route));
        }
    }

    public static List<WallRoute> preview(Object level) {
        List<WallRoute> routes = PREVIEW.get(level);
        return routes == null ? List.of() : routes;
    }
}
