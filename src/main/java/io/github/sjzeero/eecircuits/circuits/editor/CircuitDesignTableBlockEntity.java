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
 * Modified by sjzeero: ported from Create: Power Grid to EE Circuits.
 */
package io.github.sjzeero.eecircuits.circuits.editor;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import io.github.sjzeero.eecircuits.EECircuits;
import io.github.sjzeero.eecircuits.circuits.schematic.CircuitSchematic;
import io.github.sjzeero.eecircuits.circuits.schematic.ISchematicHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CircuitDesignTableBlockEntity extends BlockEntity implements IMultiScreenHandlerFactory, ISchematicHolder, IMenuContent {
    private final Container inventory = new SimpleContainer(3);

    CircuitSchematic schematic = new CircuitSchematic();
    boolean schematicChanged = false;

    public CircuitDesignTableBlockEntity(BlockPos pos, BlockState state) {
        super(EECircuits.DESIGN_TABLE_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CircuitDesignTableBlockEntity be) {
        if(!(level instanceof ServerLevel server))
            return;
        var device = DevicesSavedData.load(server).getDevice(pos, CircuitDesignTableDevice.class);
        boolean powered = device != null && device.power() >= CircuitDesignTableDevice.REQUIRED_POWER;
        if(powered != state.getValue(CircuitDesignTableBlock.POWERED))
            level.setBlock(pos, state.setValue(CircuitDesignTableBlock.POWERED, powered), Block.UPDATE_ALL_IMMEDIATE);
    }

    public boolean isPowered() {
        return getBlockState().getValue(CircuitDesignTableBlock.POWERED);
    }

    public void notifyUpdate() {
        setChanged();
        if(level != null)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Schematic", schematic.serializeNbt(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        schematic.deserializeNbt(registries, tag.getCompound("Schematic"));
        if(level != null && level.isClientSide)
            schematicChanged = true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void sendToMenu(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        var tag = new CompoundTag();
        saveAdditional(tag, buf.registryAccess());
        buf.writeNbt(tag);
    }

    @Override
    public void readClient(CompoundTag tag, HolderLookup.Provider registries) {
        schematic.deserializeNbt(registries, tag.getCompound("Schematic"));
        schematicChanged = true;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player, int menuIndex) {
        return switch(menuIndex) {
            case 0 -> CircuitDesignTableMenu.create(syncId, playerInventory, this);
            case 1 -> CircuitDesignTableEditMenu.create(syncId, playerInventory, this);
            case 2 -> CircuitDesignTableLoadMenu.create(syncId, playerInventory, this);
            default -> null;
        };
    }

    public void writeToItem(boolean isCreative) {
        if(level == null || level.isClientSide)
            return;
        if(!isCreative) {
            var stack = inventory.getItem(1);
            if(stack.isEmpty())
                return;
            stack.shrink(1);
        }
        var result = schematic.toItemStack(level.registryAccess());
        inventory.setItem(2, result);
        schematic.clear();
        notifyUpdate();
    }

    public void readFromItem() {
        var stack = inventory.getItem(0);
        if(stack.isEmpty() || level == null || level.isClientSide || !stack.has(DataComponents.CUSTOM_DATA))
            return;
        if(inventory.getItem(1).isEmpty() && stack.is(EECircuits.CIRCUIT_SCHEMATIC.get())) {
            inventory.setItem(0, ItemStack.EMPTY);
            inventory.setItem(1, stack);
        }
        try {
            schematic.deserializeNbt(level.registryAccess(), stack.get(DataComponents.CUSTOM_DATA).copyTag().getCompound("Schematic"));
        } catch(RuntimeException e) {
            EECircuits.LOGGER.error("Failed to load schematic from item: ", e);
        }
        notifyUpdate();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.eecircuits.circuit_design_table");
    }

    @Override
    @NotNull
    public String getSchematicName() {
        var name = schematic.getName();
        return name != null ? name : Component.translatable("item.eecircuits.circuit_schematic.empty").getString();
    }

    @Override
    public void setSchematicName(String name) {
        schematic.setName(name);
        if(level != null && !level.isClientSide)
            notifyUpdate();
    }

    public Container getInventory() {
        return inventory;
    }

    @Override
    public CircuitSchematic getSchematic() {
        return schematic;
    }

    @Override
    public void setSchematic(CircuitSchematic schematic) {
        this.schematic = schematic;
        if(level != null && !level.isClientSide)
            notifyUpdate();
    }
}
