package io.github.sjzeero.voltworks.redstone;

import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.redstone.client.ClientRedstoneWires;
import io.github.sjzeero.voltworks.wallwire.WallCell;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record RedstoneWiresPayload(boolean reset, List<List<WallCell>> added, List<RedstoneLinks.Key> removed) implements CustomPacketPayload {
    public static final Type<RedstoneWiresPayload> TYPE = new Type<>(Voltworks.asResource("redstone_wires"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RedstoneWiresPayload> CODEC = StreamCodec.of((buf, p) -> p.write(buf), RedstoneWiresPayload::read);

    private static RedstoneWiresPayload read(RegistryFriendlyByteBuf buf) {
        boolean reset = buf.readBoolean();
        int n = buf.readVarInt();
        List<List<WallCell>> added = new ArrayList<>(n);
        for(int i = 0; i < n; i++) {
            int m = buf.readVarInt();
            List<WallCell> cells = new ArrayList<>(m);
            for(int j = 0; j < m; j++)
                cells.add(WallCell.unpack(buf.readLong(), buf.readShort() & 0xffff));
            added.add(cells);
        }
        int k = buf.readVarInt();
        List<RedstoneLinks.Key> removed = new ArrayList<>(k);
        for(int i = 0; i < k; i++)
            removed.add(new RedstoneLinks.Key(buf.readBlockPos(), Direction.from3DDataValue(buf.readByte()), buf.readBlockPos(), Direction.from3DDataValue(buf.readByte())));
        return new RedstoneWiresPayload(reset, added, removed);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(reset);
        buf.writeVarInt(added.size());
        for(List<WallCell> cells : added) {
            buf.writeVarInt(cells.size());
            for(WallCell c : cells) {
                buf.writeLong(c.pos().asLong());
                buf.writeShort(c.packLocal());
            }
        }
        buf.writeVarInt(removed.size());
        for(RedstoneLinks.Key key : removed) {
            buf.writeBlockPos(key.src());
            buf.writeByte(key.srcFace().get3DDataValue());
            buf.writeBlockPos(key.dst());
            buf.writeByte(key.dstFace().get3DDataValue());
        }
    }

    public static void handle(RedstoneWiresPayload payload, IPayloadContext context) {
        ClientRedstoneWires.apply(payload);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
