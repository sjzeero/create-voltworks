package io.github.sjzeero.voltworks.wallwire;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import io.github.sjzeero.voltworks.Voltworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Route updates for clients. {@code reset} drops everything the client knew first (login, dimension change). */
public record WallRoutesPayload(boolean reset, List<WallRoute> added, List<InWorldNodeConnection> removed) implements CustomPacketPayload {
    public static final Type<WallRoutesPayload> TYPE = new Type<>(Voltworks.asResource("wall_routes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WallRoutesPayload> CODEC = StreamCodec.of((buf, p) -> p.write(buf), WallRoutesPayload::read);

    private static WallRoutesPayload read(RegistryFriendlyByteBuf buf) {
        boolean reset = buf.readBoolean();
        int n = buf.readVarInt();
        List<WallRoute> added = new ArrayList<>(n);
        for(int i = 0; i < n; i++)
            added.add(WallRoute.read(buf));
        int m = buf.readVarInt();
        List<InWorldNodeConnection> removed = new ArrayList<>(m);
        for(int i = 0; i < m; i++)
            removed.add(new InWorldNodeConnection(WallRoute.readNode(buf), WallRoute.readNode(buf)));
        return new WallRoutesPayload(reset, added, removed);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(reset);
        buf.writeVarInt(added.size());
        for(WallRoute r : added)
            r.write(buf);
        buf.writeVarInt(removed.size());
        for(InWorldNodeConnection c : removed) {
            WallRoute.writeNode(buf, c.node1());
            WallRoute.writeNode(buf, c.node2());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
