package io.github.sjzeero.voltworks.wallwire;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.wire.WireAttachment;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.InWorldNodeData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Wires on blocks that a Create contraption picks up. Electro Energetics drops every wire of a block that leaves the
 * world, so the wires are taken off the nodes before the blocks go, kept in the contraption (positions relative to its
 * anchor) and put back on the moved blocks when it turns into blocks again.
 */
public final class ContraptionWires {
    private ContraptionWires() {
    }

    public static ListTag capture(ServerLevel level, Contraption contraption, BlockPos offset) {
        ListTag out = new ListTag();
        BlockPos shift = contraption.anchor.offset(offset);
        Set<BlockPos> local = contraption.getBlocks().keySet();
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        WallWireData walls = WallWireData.get(level);

        Set<InWorldNodeConnection> connections = new LinkedHashSet<>();
        for(BlockPos lp : local)
            for(InWorldNodeData node : new ArrayList<>(sd.getNodesAt(lp.offset(shift))))
                connections.addAll(sd.getConnections(node));
        for(WallRoute route : walls.all())
            for(WallCell cell : route.cells)
                if(local.contains(cell.pos().subtract(shift))) {
                    connections.add(route.connection);
                    break;
                }

        for(InWorldNodeConnection connection : connections) {
            WireData data = sd.getConnectionData(connection);
            if(data == null)
                continue;
            CompoundTag tag = new CompoundTag();
            tag.put("N1", writeNode(connection.node1(), shift, local));
            tag.put("N2", writeNode(connection.node2(), shift, local));
            writeData(tag, data);
            WallRoute route = walls.get(connection);
            if(route != null) {
                long[] pos = new long[route.cells.size()];
                int[] loc = new int[pos.length];
                byte[] in = new byte[pos.length];
                for(int i = 0; i < pos.length; i++) {
                    WallCell cell = route.cells.get(i);
                    boolean inside = local.contains(cell.pos().subtract(shift));
                    pos[i] = (inside ? cell.pos().subtract(shift) : cell.pos()).asLong();
                    loc[i] = cell.packLocal();
                    in[i] = (byte) (inside ? 1 : 0);
                }
                tag.putLongArray("CPos", pos);
                tag.putIntArray("CLocal", loc);
                tag.putByteArray("CIn", in);
                tag.putDouble("RLength", route.length);
                if(route.color != null)
                    tag.putString("Color", route.color.getSerializedName());
                walls.remove(connection);
            }
            sd.removeConnectionNoDrops(connection);
            out.add(tag);
        }
        return out;
    }

    public static void restore(ServerLevel level, ListTag list, StructureTransform transform) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        WallWireData walls = WallWireData.get(level);
        for(Tag t : list) {
            CompoundTag tag = (CompoundTag) t;
            InWorldNode n1 = readNode(tag.getCompound("N1"), transform), n2 = readNode(tag.getCompound("N2"), transform);
            WireData data = readData(tag);
            if(data == null)
                continue;
            ensure(level, n1.sourcePos());
            ensure(level, n2.sourcePos());
            if(n1.equals(n2) || !sd.getNodes().contains(n1) || !sd.getNodes().contains(n2) || sd.isConnected(n1, n2)) {
                BlockPos at = n1.sourcePos();
                Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, new ItemStack(data.wireType().getSpooledItem()));
                continue;
            }
            if(tag.contains("CPos")) {
                long[] pos = tag.getLongArray("CPos");
                int[] loc = tag.getIntArray("CLocal");
                byte[] in = tag.getByteArray("CIn");
                List<WallCell> cells = new ArrayList<>(pos.length);
                for(int i = 0; i < pos.length; i++) {
                    WallCell cell = WallCell.unpack(pos[i], loc[i]);
                    cells.add(in[i] != 0 ? map(cell, transform) : cell);
                }
                WallRoute route = WallRoute.oriented(n1, n2, cells, tag.getDouble("RLength"));
                if(tag.contains("Color"))
                    route = route.withColor(DyeColor.byName(tag.getString("Color"), null));
                walls.put(route);
            }
            sd.connect(n1, n2, data);
        }
    }

    private static void ensure(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if(state.getBlock() instanceof ElectricalDeviceBlock<?> device)
            device.ensureNodesExist(level, pos, state);
    }

    private static WallCell map(WallCell cell, StructureTransform transform) {
        BlockPos p = cell.pos();
        Vec3 v = transform.apply(new Vec3(p.getX() + (cell.px() + 0.5) / 16, p.getY() + (cell.py() + 0.5) / 16, p.getZ() + (cell.pz() + 0.5) / 16));
        return WallCell.ofPixel((int) Math.floor(v.x * 16), (int) Math.floor(v.y * 16), (int) Math.floor(v.z * 16),
                transform.rotateFacing(transform.mirrorFacing(cell.face())));
    }

    private static CompoundTag writeNode(InWorldNode node, BlockPos shift, Set<BlockPos> local) {
        BlockPos rel = node.sourcePos().subtract(shift);
        boolean inside = local.contains(rel);
        CompoundTag tag = new CompoundTag();
        tag.putLong("Pos", (inside ? rel : node.sourcePos()).asLong());
        tag.putBoolean("In", inside);
        tag.putInt("ID", node.id());
        return tag;
    }

    private static InWorldNode readNode(CompoundTag tag, StructureTransform transform) {
        BlockPos pos = BlockPos.of(tag.getLong("Pos"));
        return new InWorldNode(tag.getInt("ID"), tag.getBoolean("In") ? transform.apply(pos) : pos);
    }

    private static void writeData(CompoundTag tag, WireData data) {
        ResourceLocation key = CEERegistries.WIRE_TYPE.getKey(data.wireType());
        tag.putString("Type", key == null ? "" : key.toString());
        tag.putFloat("Temp", data.temperature());
        tag.putDouble("Length", data.length);
        ListTag attachments = new ListTag();
        for(Pair<Float, WireAttachment> a : data.attachments()) {
            CompoundTag at = a.getSecond().write();
            at.putFloat("Point", a.getFirst());
            attachments.add(at);
        }
        tag.put("Attachments", attachments);
    }

    private static WireData readData(CompoundTag tag) {
        ResourceLocation key = ResourceLocation.tryParse(tag.getString("Type"));
        WireType type = key == null ? null : CEERegistries.WIRE_TYPE.get(key);
        if(type == null)
            return null;
        List<Pair<Float, WireAttachment>> attachments = new ArrayList<>();
        for(Tag t : tag.getList("Attachments", Tag.TAG_COMPOUND))
            attachments.add(Pair.of(((CompoundTag) t).getFloat("Point"), WireAttachment.read((CompoundTag) t)));
        return new WireData(type, tag.getFloat("Temp"), attachments, tag.getDouble("Length"));
    }
}
