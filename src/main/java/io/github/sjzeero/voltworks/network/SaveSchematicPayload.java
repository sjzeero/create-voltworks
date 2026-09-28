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
 * Modified by sjzeero: ported from Create: Power Grid to Create: Voltworks.
 */
package io.github.sjzeero.voltworks.network;

import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.board.BoardBlockEntity;
import io.github.sjzeero.voltworks.circuits.editor.CircuitDesignTableBlockEntity;
import io.github.sjzeero.voltworks.circuits.editor.IMultiScreenHandlerFactory;
import io.github.sjzeero.voltworks.circuits.schematic.CircuitSchematic;
import io.github.sjzeero.voltworks.circuits.schematic.ISchematicHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

public record SaveSchematicPayload(BlockPos pos, @Nullable CompoundTag nbt, @Nullable String name, boolean load) implements CustomPacketPayload {
    public static final Type<SaveSchematicPayload> TYPE = new Type<>(Voltworks.asResource("save_schematic"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveSchematicPayload> CODEC = StreamCodec.of((buf, p) -> p.write(buf), SaveSchematicPayload::read);

    public static <T extends BlockEntity & ISchematicHolder> SaveSchematicPayload action(T be, boolean load) {
        return new SaveSchematicPayload(be.getBlockPos(), null, null, load);
    }

    public static <T extends BlockEntity & ISchematicHolder> SaveSchematicPayload save(T be, @Nullable String name, CircuitSchematic schematic) {
        return new SaveSchematicPayload(be.getBlockPos(), schematic.serializeNbt(be.getLevel().registryAccess()), name, false);
    }

    private static SaveSchematicPayload read(RegistryFriendlyByteBuf buf) {
        var pos = buf.readBlockPos();
        if(buf.readBoolean()) {
            var nbt = buf.readNbt();
            String name = buf.readBoolean() ? buf.readUtf() : null;
            return new SaveSchematicPayload(pos, nbt, name, false);
        }
        return new SaveSchematicPayload(pos, null, null, buf.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(nbt != null);
        if(nbt != null) {
            buf.writeNbt(nbt);
            buf.writeBoolean(name != null);
            if(name != null)
                buf.writeUtf(name);
        } else {
            buf.writeBoolean(load);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        if(!(context.player() instanceof ServerPlayer player))
            return;
        if(!player.level().isLoaded(pos) || player.distanceToSqr(pos.getCenter()) > 64 * 64)
            return;
        var be = player.serverLevel().getBlockEntity(pos);
        if(be instanceof CircuitDesignTableBlockEntity table) {
            if(nbt != null) {
                table.getSchematic().deserializeNbt(player.serverLevel().registryAccess(), nbt);
                table.setSchematicName(name);
                table.notifyUpdate();
                IMultiScreenHandlerFactory.openScreen(player, table, table::sendToMenu, 0);
            } else if(load) {
                table.readFromItem();
            } else {
                table.writeToItem(player.isCreative());
            }
        } else if(be instanceof BoardBlockEntity board) {
            if(player.isCreative() && nbt != null)
                board.setSchematic(CircuitSchematic.fromNbt(player.serverLevel().registryAccess(), nbt));
        }
    }
}
