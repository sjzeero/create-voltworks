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
 * Modified by sjzeero: ported from Create: Power Grid to Voltworks.
 */
package io.github.sjzeero.voltworks.network;

import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.board.BoardBlockEntity;
import io.github.sjzeero.voltworks.board.BoardDevice;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import io.github.sjzeero.voltworks.circuits.components.properties.ComponentProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;

public record UpdateComponentPayload(BlockPos pos, UUID component, ResourceLocation propertyId, CompoundTag value) implements CustomPacketPayload {
    public static final Type<UpdateComponentPayload> TYPE = new Type<>(Voltworks.asResource("update_component"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateComponentPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, UpdateComponentPayload::pos,
            UUIDUtil.STREAM_CODEC, UpdateComponentPayload::component,
            ResourceLocation.STREAM_CODEC, UpdateComponentPayload::propertyId,
            ByteBufCodecs.COMPOUND_TAG, UpdateComponentPayload::value,
            UpdateComponentPayload::new);

    public static UpdateComponentPayload of(BoardBlockEntity be, PlacedComponent placed, ComponentProperty<?> property) {
        var tag = new CompoundTag();
        placed.getEntry(property).write(be.getLevel().registryAccess(), tag);
        return new UpdateComponentPayload(be.getBlockPos(), placed.uuid, property.id(), tag);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        if(!(context.player() instanceof ServerPlayer player))
            return;
        var level = player.serverLevel();
        if(!player.mayInteract(level, pos) || player.distanceToSqr(pos.getCenter()) > 16 * 16)
            return;
        if(!(level.getBlockEntity(pos) instanceof BoardBlockEntity))
            return;
        var device = DevicesSavedData.load(level).getDevice(pos, BoardDevice.class);
        if(device != null)
            device.queuePropertyUpdate(component, propertyId, value);
    }
}
