package io.github.sjzeero.voltworks.wallwire;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where a wall wire runs. The cells go from {@code connection.node1()} to {@code connection.node2()};
 * the ends of the wire are straight leads from each node to the first and last cell.
 */
public final class WallRoute {
    public final InWorldNodeConnection connection;
    public final List<WallCell> cells;
    public final double length;

    public WallRoute(InWorldNodeConnection connection, List<WallCell> cells, double length) {
        this.connection = connection;
        this.cells = List.copyOf(cells);
        this.length = length;
    }

    /** Builds a route from cells ordered from {@code start}, flipping them if {@code start} is not node1. */
    public static WallRoute oriented(InWorldNode start, InWorldNode end, List<WallCell> cellsFromStart, double length) {
        InWorldNodeConnection connection = new InWorldNodeConnection(start, end);
        List<WallCell> cells = new ArrayList<>(cellsFromStart);
        if(!connection.node1().equals(start))
            Collections.reverse(cells);
        return new WallRoute(connection, cells, length);
    }

    public double surfaceLength() {
        return (cells.size() - 1) / 16.0;
    }

    /** Points along the whole wire for Electro Energetics' own bookkeeping (sections, contacts, particles). */
    public List<Vec3> samplePoints(Vec3 pos1, Vec3 pos2) {
        List<Vec3> out = new ArrayList<>();
        out.add(pos1);
        for(int i = 0; i < cells.size(); i += 8)
            out.add(cells.get(i).point());
        out.add(cells.getLast().point());
        out.add(pos2);
        return out;
    }

    public AABB bounds(Vec3 pos1, Vec3 pos2) {
        AABB bb = new AABB(pos1, pos2);
        for(WallCell c : cells)
            bb = bb.minmax(new AABB(c.point(), c.point()));
        return bb;
    }

    public record Segment(Vec3 a, Vec3 b, Direction normal, BlockPos lightPos) {
        public double length() {
            return a.distanceTo(b);
        }
    }

    /** Straight flat runs along the faces, merged where they continue in the same direction. */
    public List<Segment> surfaceSegments() {
        return segments(cells);
    }

    public static List<Segment> segments(List<WallCell> cells) {
        List<Segment> raw = new ArrayList<>();
        for(int i = 0; i + 1 < cells.size(); i++) {
            WallCell a = cells.get(i), b = cells.get(i + 1);
            Vec3 pa = a.point(), pb = b.point();
            if(a.face() == b.face()) {
                raw.add(new Segment(pa, pb, a.face(), a.front()));
            } else {
                int axis = b.face().getAxis().ordinal();
                double[] e = {pa.x, pa.y, pa.z};
                e[axis] = axis == 0 ? pb.x : axis == 1 ? pb.y : pb.z;
                Vec3 corner = new Vec3(e[0], e[1], e[2]);
                raw.add(new Segment(pa, corner, a.face(), a.front()));
                raw.add(new Segment(corner, pb, b.face(), b.front()));
            }
        }
        List<Segment> merged = new ArrayList<>();
        for(Segment s : raw) {
            if(s.length() < 1e-6)
                continue;
            if(!merged.isEmpty()) {
                Segment last = merged.getLast();
                if(last.normal() == s.normal() && last.b().distanceToSqr(s.a()) < 1e-9
                        && last.b().subtract(last.a()).normalize().dot(s.b().subtract(s.a()).normalize()) > 0.9999) {
                    merged.set(merged.size() - 1, new Segment(last.a(), s.b(), s.normal(), last.lightPos()));
                    continue;
                }
            }
            merged.add(s);
        }
        return merged;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Node1", nodeTag(connection.node1()));
        tag.put("Node2", nodeTag(connection.node2()));
        long[] pos = new long[cells.size()];
        int[] local = new int[cells.size()];
        for(int i = 0; i < cells.size(); i++) {
            pos[i] = cells.get(i).pos().asLong();
            local[i] = cells.get(i).packLocal();
        }
        tag.putLongArray("Pos", pos);
        tag.putIntArray("Local", local);
        tag.putDouble("Length", length);
        return tag;
    }

    public static WallRoute load(CompoundTag tag) {
        InWorldNode n1 = InWorldNode.readFromTag(tag.getCompound("Node1"));
        InWorldNode n2 = InWorldNode.readFromTag(tag.getCompound("Node2"));
        long[] pos = tag.getLongArray("Pos");
        int[] local = tag.getIntArray("Local");
        List<WallCell> cells = new ArrayList<>(pos.length);
        for(int i = 0; i < Math.min(pos.length, local.length); i++)
            cells.add(WallCell.unpack(pos[i], local[i]));
        return new WallRoute(new InWorldNodeConnection(n1, n2), cells, tag.getDouble("Length"));
    }

    private static CompoundTag nodeTag(InWorldNode node) {
        CompoundTag tag = new CompoundTag();
        tag.put("Pos", NbtUtils.writeBlockPos(node.sourcePos()));
        tag.putInt("ID", node.id());
        return tag;
    }

    public void write(FriendlyByteBuf buf) {
        writeNode(buf, connection.node1());
        writeNode(buf, connection.node2());
        buf.writeDouble(length);
        buf.writeVarInt(cells.size());
        for(WallCell c : cells) {
            buf.writeLong(c.pos().asLong());
            buf.writeShort(c.packLocal());
        }
    }

    public static WallRoute read(FriendlyByteBuf buf) {
        InWorldNode n1 = readNode(buf), n2 = readNode(buf);
        double length = buf.readDouble();
        int n = buf.readVarInt();
        List<WallCell> cells = new ArrayList<>(n);
        for(int i = 0; i < n; i++)
            cells.add(WallCell.unpack(buf.readLong(), buf.readShort() & 0xffff));
        return new WallRoute(new InWorldNodeConnection(n1, n2), cells, length);
    }

    static void writeNode(FriendlyByteBuf buf, InWorldNode node) {
        buf.writeVarInt(node.id());
        buf.writeBlockPos(node.sourcePos());
    }

    static InWorldNode readNode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        return new InWorldNode(id, buf.readBlockPos());
    }
}
