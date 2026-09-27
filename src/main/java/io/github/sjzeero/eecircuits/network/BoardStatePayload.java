package io.github.sjzeero.eecircuits.network;

import io.github.sjzeero.eecircuits.EECircuits;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record BoardStatePayload(BlockPos pos, int version, List<Change> changes, float @Nullable [] temperatures,
                                int @Nullable [] burnt, int light) implements CustomPacketPayload {
    public static final Type<BoardStatePayload> TYPE = new Type<>(EECircuits.asResource("board_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BoardStatePayload> CODEC = StreamCodec.of((buf, p) -> p.write(buf), BoardStatePayload::read);

    public record Change(int component, ResourceLocation property, CompoundTag value) {
    }

    private static BoardStatePayload read(RegistryFriendlyByteBuf buf) {
        var pos = buf.readBlockPos();
        int version = buf.readVarInt();
        int n = buf.readVarInt();
        var changes = new ArrayList<Change>(n);
        for(int i = 0; i < n; i++)
            changes.add(new Change(buf.readVarInt(), buf.readResourceLocation(), buf.readNbt()));
        float[] temps = null;
        if(buf.readBoolean()) {
            temps = new float[buf.readVarInt()];
            for(int i = 0; i < temps.length; i++)
                temps[i] = buf.readFloat();
        }
        int[] burnt = buf.readBoolean() ? buf.readVarIntArray() : null;
        int light = buf.readByte();
        return new BoardStatePayload(pos, version, changes, temps, burnt, light);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(version);
        buf.writeVarInt(changes.size());
        for(var change : changes) {
            buf.writeVarInt(change.component());
            buf.writeResourceLocation(change.property());
            buf.writeNbt(change.value());
        }
        buf.writeBoolean(temperatures != null);
        if(temperatures != null) {
            buf.writeVarInt(temperatures.length);
            for(float t : temperatures)
                buf.writeFloat(t);
        }
        buf.writeBoolean(burnt != null);
        if(burnt != null)
            buf.writeVarIntArray(burnt);
        buf.writeByte(light);
    }

    public boolean isEmpty() {
        return changes.isEmpty() && temperatures == null && burnt == null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
