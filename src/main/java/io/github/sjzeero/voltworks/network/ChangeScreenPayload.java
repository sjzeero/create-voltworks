/*
 * Copyright 2025 patryk3211
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified by sjzeero: ported from Create: Power Grid to CEE: Voltworks.
 */
package io.github.sjzeero.voltworks.network;

import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.editor.CircuitDesignTableBlockEntity;
import io.github.sjzeero.voltworks.circuits.editor.IMultiScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ChangeScreenPayload(BlockPos pos, int screenIndex) implements CustomPacketPayload {
    public static final Type<ChangeScreenPayload> TYPE = new Type<>(Voltworks.asResource("change_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChangeScreenPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ChangeScreenPayload::pos,
            ByteBufCodecs.VAR_INT, ChangeScreenPayload::screenIndex,
            ChangeScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        if(!(context.player() instanceof ServerPlayer player))
            return;
        if(!player.level().isLoaded(pos) || player.distanceToSqr(pos.getCenter()) > 64 * 64)
            return;
        if(player.level().getBlockEntity(pos) instanceof CircuitDesignTableBlockEntity table)
            IMultiScreenHandlerFactory.openScreen(player, table, table::sendToMenu, screenIndex);
    }
}
