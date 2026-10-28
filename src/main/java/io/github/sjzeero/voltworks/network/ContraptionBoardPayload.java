package io.github.sjzeero.voltworks.network;

import io.github.sjzeero.voltworks.Voltworks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

public record ContraptionBoardPayload(int entity, BlockPos pos, @Nullable CompoundTag full, @Nullable BoardStatePayload state) implements CustomPacketPayload {
    public static final Type<ContraptionBoardPayload> TYPE = new Type<>(Voltworks.asResource("contraption_board"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContraptionBoardPayload> CODEC = StreamCodec.of(ContraptionBoardPayload::write, ContraptionBoardPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ContraptionBoardPayload p) {
        buf.writeVarInt(p.entity);
        buf.writeBlockPos(p.pos);
        buf.writeBoolean(p.full != null);
        if(p.full != null)
            buf.writeNbt(p.full);
        buf.writeBoolean(p.state != null);
        if(p.state != null)
            BoardStatePayload.CODEC.encode(buf, p.state);
    }

    private static ContraptionBoardPayload read(RegistryFriendlyByteBuf buf) {
        int entity = buf.readVarInt();
        BlockPos pos = buf.readBlockPos();
        CompoundTag full = buf.readBoolean() ? buf.readNbt() : null;
        BoardStatePayload state = buf.readBoolean() ? BoardStatePayload.CODEC.decode(buf) : null;
        return new ContraptionBoardPayload(entity, pos, full, state);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
