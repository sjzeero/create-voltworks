package io.github.sjzeero.voltworks.wallwire;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import io.github.sjzeero.voltworks.Voltworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Sent when a player finishes a wall wire: the two nodes and the face points they clicked, in order. */
public record PlaceWallWirePayload(InWorldNode start, InWorldNode end, List<WallCell> points) implements CustomPacketPayload {
    public static final int MAX_POINTS = 512;
    public static final Type<PlaceWallWirePayload> TYPE = new Type<>(Voltworks.asResource("place_wall_wire"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceWallWirePayload> CODEC = StreamCodec.of((buf, p) -> p.write(buf), PlaceWallWirePayload::read);

    private static PlaceWallWirePayload read(RegistryFriendlyByteBuf buf) {
        InWorldNode start = WallRoute.readNode(buf), end = WallRoute.readNode(buf);
        int n = Math.min(buf.readVarInt(), MAX_POINTS);
        List<WallCell> points = new ArrayList<>(n);
        for(int i = 0; i < n; i++)
            points.add(WallCell.unpack(buf.readLong(), buf.readShort() & 0xffff));
        return new PlaceWallWirePayload(start, end, points);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        WallRoute.writeNode(buf, start);
        WallRoute.writeNode(buf, end);
        buf.writeVarInt(points.size());
        for(WallCell c : points) {
            buf.writeLong(c.pos().asLong());
            buf.writeShort(c.packLocal());
        }
    }

    public static void handle(PlaceWallWirePayload payload, IPayloadContext context) {
        if(context.player() instanceof ServerPlayer player)
            WallWirePlacement.place(player, payload);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
