package io.github.sjzeero.voltworks.wallwire;

import com.george_vi.electroenergetics.devices.device.DeviceBlock;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.simibubi.create.content.contraptions.Contraption;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.board.BoardBlock;
import io.github.sjzeero.voltworks.board.BoardDevice;
import io.github.sjzeero.voltworks.network.BoardStatePayload;
import io.github.sjzeero.voltworks.network.ContraptionBoardPayload;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FrozenDevices extends SavedData {
    private static final String NAME = "voltworks_frozen_devices";

    public record Wire(InWorldNode a, InWorldNode b, double resistance) {
    }

    public record Group(UUID id, long[] rel, long[] world, List<Wire> wires) {
    }

    private final Map<UUID, Group> groups = new HashMap<>();
    private final LongOpenHashSet positions = new LongOpenHashSet();

    public static FrozenDevices get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(FrozenDevices::new, FrozenDevices::load), NAME);
    }

    private static FrozenDevices load(CompoundTag tag, HolderLookup.Provider provider) {
        FrozenDevices data = new FrozenDevices();
        for(Tag t : tag.getList("Groups", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag g = (CompoundTag) t;
                List<Wire> wires = new ArrayList<>();
                for(Tag w : g.getList("Wires", Tag.TAG_COMPOUND)) {
                    CompoundTag wt = (CompoundTag) w;
                    wires.add(new Wire(readNode(wt.getIntArray("A")), readNode(wt.getIntArray("B")), wt.getDouble("R")));
                }
                data.put(new Group(UUID.fromString(g.getString("Id")), g.getLongArray("Rel"), g.getLongArray("World"), wires));
            } catch(RuntimeException e) {
                Voltworks.LOGGER.warn("voltworks: frozen group skipped: {}", String.valueOf(e));
            }
        }
        return data;
    }

    private static InWorldNode readNode(int[] a) {
        return new InWorldNode(a[0], a[1], a[2], a[3]);
    }

    private static int[] writeNode(InWorldNode n) {
        return new int[]{n.id(), n.sourcePos().getX(), n.sourcePos().getY(), n.sourcePos().getZ()};
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        ListTag list = new ListTag();
        for(Group g : groups.values()) {
            CompoundTag gt = new CompoundTag();
            gt.putString("Id", g.id().toString());
            gt.putLongArray("Rel", g.rel());
            gt.putLongArray("World", g.world());
            ListTag wires = new ListTag();
            for(Wire w : g.wires()) {
                CompoundTag wt = new CompoundTag();
                wt.putIntArray("A", writeNode(w.a()));
                wt.putIntArray("B", writeNode(w.b()));
                wt.putDouble("R", w.resistance());
                wires.add(wt);
            }
            gt.put("Wires", wires);
            list.add(gt);
        }
        tag.put("Groups", list);
        return tag;
    }

    private void put(Group g) {
        groups.put(g.id(), g);
        for(long l : g.world())
            positions.add(l);
        setDirty();
    }

    public boolean isFrozen(BlockPos pos) {
        return !positions.isEmpty() && positions.contains(pos.asLong());
    }

    public int groupCount() {
        return groups.size();
    }

    public int deviceCount() {
        return positions.size();
    }

    public @Nullable UUID freeze(ServerLevel level, Contraption contraption, BlockPos offset, ListTag captured) {
        BlockPos shift = contraption.anchor.offset(offset);
        List<Long> rel = new ArrayList<>();
        List<Long> world = new ArrayList<>();
        for(BlockPos lp : contraption.getBlocks().keySet()) {
            BlockPos wp = lp.offset(shift);
            if(level.getBlockState(wp).getBlock() instanceof DeviceBlock<?> && !isFrozen(wp)) {
                rel.add(lp.asLong());
                world.add(wp.asLong());
            }
        }
        if(rel.isEmpty())
            return null;
        List<Wire> wires = new ArrayList<>();
        for(Tag t : captured) {
            CompoundTag tag = (CompoundTag) t;
            CompoundTag n1 = tag.getCompound("N1"), n2 = tag.getCompound("N2");
            if(!n1.getBoolean("In") || !n2.getBoolean("In"))
                continue;
            WireData data = ContraptionWires.readData(tag);
            if(data == null)
                continue;
            InWorldNode a = new InWorldNode(n1.getInt("ID"), BlockPos.of(n1.getLong("Pos")).offset(shift));
            InWorldNode b = new InWorldNode(n2.getInt("ID"), BlockPos.of(n2.getLong("Pos")).offset(shift));
            if(!a.equals(b))
                wires.add(new Wire(a, b, data.getResistance()));
        }
        Group g = new Group(UUID.randomUUID(), rel.stream().mapToLong(Long::longValue).toArray(), world.stream().mapToLong(Long::longValue).toArray(), wires);
        put(g);
        return g.id();
    }

    public void thaw(ServerLevel level, UUID id, StructureTransform transform) {
        Group g = groups.remove(id);
        if(g == null)
            return;
        positions.clear();
        for(Group other : groups.values())
            for(long l : other.world())
                positions.add(l);
        setDirty();

        DevicesSavedData devices = DevicesSavedData.load(level);
        int n = g.rel().length;
        BlockPos[] from = new BlockPos[n], to = new BlockPos[n];
        CompoundTag[] data = new CompoundTag[n];
        for(int i = 0; i < n; i++) {
            from[i] = BlockPos.of(g.world()[i]);
            to[i] = transform.apply(BlockPos.of(g.rel()[i]));
            try {
                SimulatedDevice device = devices.getDevice(from[i]);
                if(device != null && !from[i].equals(to[i])) {
                    data[i] = new CompoundTag();
                    device.write(data[i]);
                }
            } catch(Throwable t) {
                data[i] = null;
                Voltworks.LOGGER.warn("voltworks: device state not carried: {}", String.valueOf(t));
            }
        }
        for(int i = 0; i < n; i++) {
            if(data[i] == null)
                continue;
            try {
                SimulatedDevice target = devices.getDevice(to[i]);
                SimulatedDevice source = devices.getDevice(from[i]);
                if(target != null && target != source && source != null && target.type == source.type)
                    target.read(data[i]);
            } catch(Throwable t) {
                Voltworks.LOGGER.warn("voltworks: device state not applied: {}", String.valueOf(t));
            }
        }
        for(int i = 0; i < n; i++) {
            try {
                if(from[i].equals(to[i]))
                    continue;
                boolean occupied = level.getBlockState(from[i]).getBlock() instanceof DeviceBlock<?>;
                if(!occupied)
                    devices.removeDevice(from[i]);
            } catch(Throwable t) {
                Voltworks.LOGGER.warn("voltworks: frozen device not removed: {}", String.valueOf(t));
            }
        }
    }

    public @Nullable BlockPos worldPos(@Nullable UUID id, BlockPos rel) {
        Group g = id == null ? null : groups.get(id);
        if(g == null)
            return null;
        long key = rel.asLong();
        for(int i = 0; i < g.rel().length; i++)
            if(g.rel()[i] == key)
                return BlockPos.of(g.world()[i]);
        return null;
    }

    private record Sent(int entity, long pos) {
    }

    private static final Map<Sent, long[]> SENT = new HashMap<>();
    private static final int RESEND = 100;

    public static void startTracking(PlayerEvent.StartTracking event) {
        if(event.getTarget() instanceof AbstractContraptionEntity entity)
            SENT.keySet().removeIf(k -> k.entity() == entity.getId());
    }

    public static void forward(ServerLevel level) {
        try {
            FrozenDevices data = get(level);
            if(data.groups.isEmpty()) {
                SENT.clear();
                return;
            }
            DevicesSavedData devices = DevicesSavedData.load(level);
            for(Entity e : level.getAllEntities()) {
                if(!(e instanceof AbstractContraptionEntity entity) || !(entity.getContraption() instanceof ContraptionWireView view))
                    continue;
                Group g = view.voltworks$frozenId() == null ? null : data.groups.get(view.voltworks$frozenId());
                if(g == null)
                    continue;
                for(int i = 0; i < g.rel().length; i++) {
                    BlockPos rel = BlockPos.of(g.rel()[i]);
                    var info = entity.getContraption().getBlocks().get(rel);
                    if(info == null || !(info.state().getBlock() instanceof BoardBlock))
                        continue;
                    var device = devices.getDevice(BlockPos.of(g.world()[i]), BoardDevice.class);
                    var full = device == null ? null : device.fullTag();
                    if(full == null)
                        continue;
                    Sent key = new Sent(entity.getId(), g.rel()[i]);
                    long version = full.getInt("Version");
                    long now = level.getGameTime();
                    long[] last = SENT.get(key);
                    if(last == null || last[0] != version || now - last[1] >= RESEND) {
                        SENT.put(key, new long[]{version, now});
                        var snapshot = device.snapshotState();
                        while(device.pollState() != null) {
                        }
                        PacketDistributor.sendToPlayersTrackingEntity(entity,
                                new ContraptionBoardPayload(entity.getId(), rel, full, snapshot));
                    } else {
                        BoardStatePayload state;
                        while((state = device.pollState()) != null)
                            PacketDistributor.sendToPlayersTrackingEntity(entity,
                                    new ContraptionBoardPayload(entity.getId(), rel, null, state));
                    }
                }
            }
        } catch(Throwable t) {
            Voltworks.LOGGER.warn("voltworks: frozen board sync skipped: {}", String.valueOf(t));
        }
    }

    public static void addToGraph(AddToElectricGraphEvent event) {
        try {
            FrozenDevices data = get(event.level);
            if(data.groups.isEmpty())
                return;
            for(Group g : data.groups.values())
                for(Wire w : g.wires())
                    event.builder.connect(w.a(), w.b(), ElectricalProperties.resistor(w.resistance()));
        } catch(Throwable t) {
            Voltworks.LOGGER.warn("voltworks: frozen circuit skipped: {}", String.valueOf(t));
        }
    }
}
