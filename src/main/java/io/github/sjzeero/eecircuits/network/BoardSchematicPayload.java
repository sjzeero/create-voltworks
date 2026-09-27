package io.github.sjzeero.eecircuits.network;

import io.github.sjzeero.eecircuits.EECircuits;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BoardSchematicPayload(BlockPos pos, CompoundTag tag) implements CustomPacketPayload {
    public static final Type<BoardSchematicPayload> TYPE = new Type<>(EECircuits.asResource("board_schematic"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BoardSchematicPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BoardSchematicPayload::pos,
            ByteBufCodecs.COMPOUND_TAG, BoardSchematicPayload::tag,
            BoardSchematicPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
