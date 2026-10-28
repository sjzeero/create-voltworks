package io.github.sjzeero.voltworks.redstone;

import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.wallwire.WallCell;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record PlaceRedstoneWirePayload(List<WallCell> points) implements CustomPacketPayload {
    public static final int MAX_POINTS = 512;
    public static final Type<PlaceRedstoneWirePayload> TYPE = new Type<>(Voltworks.asResource("place_redstone_wire"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceRedstoneWirePayload> CODEC = StreamCodec.of((buf, p) -> p.write(buf), PlaceRedstoneWirePayload::read);

    private static PlaceRedstoneWirePayload read(RegistryFriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_POINTS);
        List<WallCell> points = new ArrayList<>(n);
        for(int i = 0; i < n; i++)
            points.add(WallCell.unpack(buf.readLong(), buf.readShort() & 0xffff));
        return new PlaceRedstoneWirePayload(points);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(points.size());
        for(WallCell c : points) {
            buf.writeLong(c.pos().asLong());
            buf.writeShort(c.packLocal());
        }
    }

    public static void handle(PlaceRedstoneWirePayload payload, IPayloadContext context) {
        if(context.player() instanceof ServerPlayer player)
            RedstoneWirePlacement.place(player, payload);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
