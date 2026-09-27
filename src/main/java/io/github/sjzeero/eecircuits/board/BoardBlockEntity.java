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
package io.github.sjzeero.eecircuits.board;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import io.github.sjzeero.eecircuits.EECircuits;
import io.github.sjzeero.eecircuits.circuits.components.IComponentGoggleInformation;
import io.github.sjzeero.eecircuits.circuits.components.IInteractableComponent;
import io.github.sjzeero.eecircuits.circuits.components.TerminalBoundingBox;
import io.github.sjzeero.eecircuits.circuits.schematic.BoardContext;
import io.github.sjzeero.eecircuits.circuits.schematic.CircuitSchematic;
import io.github.sjzeero.eecircuits.circuits.schematic.PlacedComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BoardBlockEntity extends BlockEntity implements BoardContext, IHaveGoggleInformation {
    private CompoundTag data = new CompoundTag();
    private CircuitSchematic schematic = new CircuitSchematic();
    private final Map<String, Float> temperatures = new HashMap<>();
    private List<TerminalBoundingBox> terminals = List.of();
    private List<Interactable> interactables = List.of();
    private int burnt;
    private int light;
    private @Nullable CompoundTag pendingSchematic;
    private int seenVersion = -1;

    public record Interactable(UUID uuid, AABB box) {
    }

    public BoardBlockEntity(BlockPos pos, BlockState state) {
        super(EECircuits.BOARD_BE.get(), pos, state);
    }

    @Override
    public @Nullable Level level() {
        return level;
    }

    @Override
    public BlockPos pos() {
        return worldPosition;
    }

    @Override
    public Vec3 toWorld(Vec3 local) {
        return BoardBlock.toWorld(getBlockState(), worldPosition, local);
    }

    public void setSchematic(CompoundTag schematicTag) {
        pendingSchematic = schematicTag.copy();
        var tag = new CompoundTag();
        tag.put("Schematic", pendingSchematic.copy());
        apply(tag, level == null ? null : level.registryAccess());
        setChanged();
    }

    public CircuitSchematic getSchematic() {
        return schematic;
    }

    public List<TerminalBoundingBox> terminals() {
        return terminals;
    }

    public List<Interactable> interactables() {
        return interactables;
    }

    public int seenVersion() {
        return seenVersion;
    }

    public int light() {
        return light;
    }

    public float temperature(String key) {
        return temperatures.getOrDefault(key, Float.NaN);
    }

    private void apply(CompoundTag tag, @Nullable HolderLookup.Provider registries) {
        data = tag;
        if(registries == null)
            return;
        try {
            schematic = CircuitSchematic.fromNbt(registries, tag.getCompound("Schematic"));
        } catch(RuntimeException e) {
            EECircuits.LOGGER.warn("Failed to read board schematic at {}", worldPosition, e);
            schematic = new CircuitSchematic();
        }
        temperatures.clear();
        var thermal = tag.getCompound("Thermal");
        for(var key : thermal.getAllKeys())
            temperatures.put(key, thermal.getFloat(key));
        light = tag.getInt("Light");

        var terms = new ArrayList<TerminalBoundingBox>();
        var inter = new ArrayList<Interactable>();
        burnt = 0;
        var burntIds = new java.util.HashSet<String>();
        for(var entry : tag.getList("Burnt", net.minecraft.nbt.Tag.TAG_STRING))
            burntIds.add(entry.getAsString());
        for(var placed : schematic.components()) {
            placed.destroyed = burntIds.contains(placed.uuid.toString());
            placed.withContext(this);
            int maxIndex = -1;
            for(var pad : placed.footprint().getPads().values())
                maxIndex = Math.max(maxIndex, pad.nodeIndex());
            boolean external = false;
            for(int i = 0; i <= maxIndex; i++)
                external |= placed.component.isExternalNode(i);
            if(external) {
                for(var bb : placed.component.terminals(placed))
                    terms.add(bb.offset(placed.x / 16f, 2 / 16f, placed.y / 16f));
            }
            if(placed.component instanceof IInteractableComponent interactable)
                inter.add(new Interactable(placed.uuid, interactable.getShape(placed).bounds()));
            if(placed.destroyed)
                burnt++;
        }
        terminals = List.copyOf(terms);
        interactables = List.copyOf(inter);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoardBlockEntity be) {
        if(!(level instanceof ServerLevel server))
            return;
        var device = DevicesSavedData.load(server).getDevice(pos, BoardDevice.class);
        if(device == null)
            return;
        if(be.pendingSchematic != null) {
            device.queueSchematic(be.pendingSchematic);
            be.pendingSchematic = null;
        }
        if(device.version() != be.seenVersion && device.clientTag() != null) {
            be.seenVersion = device.version();
            int oldTerminals = be.terminals.size();
            be.apply(device.clientTag(), level.registryAccess());
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            if(oldTerminals != be.terminals.size() || device.registeredPins != be.terminals.size())
                level.scheduleTick(pos, state.getBlock(), 1);
            if(state.getValue(BoardBlock.LIGHT) != be.light)
                level.setBlock(pos, state.setValue(BoardBlock.LIGHT, be.light), Block.UPDATE_CLIENTS);
        }
        if(device.takeRedstoneDirty())
            level.updateNeighborsAt(pos, state.getBlock());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Board", data.copy());
        if(pendingSchematic != null)
            tag.put("Pending", pendingSchematic.copy());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        apply(tag.getCompound("Board"), registries);
        if(tag.contains("Pending"))
            pendingSchematic = tag.getCompound("Pending");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.put("Board", data.copy());
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.eecircuits.circuit_board")));
        boolean any = false;
        for(var placed : schematic.components()) {
            if(placed.component instanceof IComponentGoggleInformation info)
                any |= info.addToGoggleTooltip(placed, tooltip, isPlayerSneaking);
        }
        if(burnt > 0)
            tooltip.add(Component.literal("    ").append(Component.translatable("eecircuits.goggles.burnt", burnt).withStyle(ChatFormatting.RED)));
        if(isPlayerSneaking) {
            float max = Float.NEGATIVE_INFINITY;
            for(var t : temperatures.values())
                max = Math.max(max, t);
            if(Float.isFinite(max))
                tooltip.add(Component.literal("    ").append(Component.translatable("eecircuits.goggles.hottest", String.format("%.1f", max)).withStyle(ChatFormatting.GRAY)));
        }
        return true;
    }
}
