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
package io.github.sjzeero.voltworks.board;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.circuitboard.BakedCircuit;
import io.github.sjzeero.voltworks.circuits.components.IComponentGoggleInformation;
import io.github.sjzeero.voltworks.circuits.components.IInteractableComponent;
import io.github.sjzeero.voltworks.circuits.components.TerminalBoundingBox;
import io.github.sjzeero.voltworks.circuits.editor.IMenuContent;
import io.github.sjzeero.voltworks.circuits.schematic.BoardContext;
import io.github.sjzeero.voltworks.circuits.schematic.CircuitSchematic;
import io.github.sjzeero.voltworks.circuits.schematic.ISchematicHolder;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import io.github.sjzeero.voltworks.network.BoardSchematicPayload;
import io.github.sjzeero.voltworks.network.BoardStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.github.sjzeero.voltworks.registry.VoltworksBlockEntities;
import io.github.sjzeero.voltworks.circuits.components.ComponentRegistry;
import io.github.sjzeero.voltworks.client.ClientHooks;

public class BoardBlockEntity extends BlockEntity implements BoardContext, IHaveGoggleInformation, ISchematicHolder, IMenuContent {
    private CircuitSchematic schematic = new CircuitSchematic();
    private @Nullable BakedCircuit baked;
    private float[] temperatures = new float[0];
    private int[] burntIndices = new int[0];
    private List<TerminalBoundingBox> terminals = List.of();
    private List<Interactable> interactables = List.of();
    private int burnt;
    private int light;
    private int version = -1;
    private @Nullable CompoundTag pendingSchematic;

    public @Nullable Object quads;
    public @Nullable Object clientState;
    public int modelVersion;

    public record Interactable(UUID uuid, AABB box, boolean clientScreen) {
    }

    public BoardBlockEntity(BlockPos pos, BlockState state) {
        super(VoltworksBlockEntities.BOARD_BE.get(), pos, state);
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
        setChanged();
    }

    @Override
    public CircuitSchematic getSchematic() {
        return schematic;
    }

    @Override
    public void setSchematic(CircuitSchematic schematic) {
        if(level != null)
            setSchematic(schematic.serializeNbt(level.registryAccess()));
    }

    @Override
    public @NotNull String getSchematicName() {
        var name = schematic.getName();
        return name == null ? "" : name;
    }

    @Override
    public void setSchematicName(String name) {
        schematic.setName(name);
    }

    public @Nullable BakedCircuit baked() {
        return baked;
    }

    public List<TerminalBoundingBox> terminals() {
        return terminals;
    }

    public List<Interactable> interactables() {
        return interactables;
    }

    public int version() {
        return version;
    }

    public int light() {
        return light;
    }

    public int burnt() {
        return burnt;
    }

    public float[] temperatures() {
        return temperatures;
    }

    public void applyFull(CompoundTag tag, HolderLookup.Provider registries) {
        version = tag.getInt("Version");
        try {
            schematic = CircuitSchematic.fromNbt(registries, tag.getCompound("Schematic"));
        } catch(RuntimeException e) {
            Voltworks.LOGGER.warn("Failed to read board schematic at {}", worldPosition, e);
            schematic = new CircuitSchematic();
        }
        baked = BakedCircuit.from(schematic, this);
        var list = tag.getList("Temperatures", Tag.TAG_FLOAT);
        var temps = new float[list.size()];
        for(int i = 0; i < temps.length; i++)
            temps[i] = list.getFloat(i);
        applyTemperatures(temps);
        applyBurnt(tag.getIntArray("Burnt"));
        light = tag.getInt("Light");

        var terms = new ArrayList<TerminalBoundingBox>();
        var inter = new ArrayList<Interactable>();
        for(var placed : schematic.components()) {
            if(placed.component instanceof IInteractableComponent interactable)
                inter.add(new Interactable(placed.uuid, interactable.getShape(placed).bounds(), interactable.hasClientScreen(placed)));
        }
        terms.addAll(baked.terminals);
        terminals = List.copyOf(terms);
        interactables = List.copyOf(inter);
        modelChanged();
    }

    private void modelChanged() {
        quads = null;
        modelVersion++;
        if(level != null && level.isClientSide) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

    public boolean applyState(BoardStatePayload state, HolderLookup.Provider registries) {
        if(state.version() != version)
            return false;
        boolean modelChanged = false;
        var components = schematic.components();
        for(var change : state.changes()) {
            if(change.component() < 0 || change.component() >= components.size())
                continue;
            var placed = components.get(change.component());
            var modelBefore = placed.component.getModelId(placed);
            try {
                placed.getEntry(change.property()).read(registries, change.value());
            } catch(IllegalArgumentException e) {
                continue;
            }
            placed.stateUpdated();
            modelChanged |= !modelBefore.equals(placed.component.getModelId(placed));
        }
        if(state.temperatures() != null)
            applyTemperatures(state.temperatures());
        if(state.burnt() != null) {
            applyBurnt(state.burnt());
            modelChanged = true;
        }
        light = state.light();
        if(modelChanged)
            modelChanged();
        return modelChanged;
    }

    private void applyTemperatures(float[] temps) {
        temperatures = temps;
        if(baked == null)
            return;
        int n = Math.min(temps.length, baked.thermalUnits.size());
        for(int i = 0; i < n; i++)
            baked.thermalUnits.get(i).setTemperature(temps[i]);
    }

    private void applyBurnt(int[] indices) {
        burntIndices = indices;
        var components = schematic.components();
        for(var placed : components)
            placed.destroyed = false;
        for(int i : indices)
            if(i >= 0 && i < components.size())
                components.get(i).destroyed = true;
        burnt = indices.length;
    }

    public CompoundTag writeClient(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt("Version", version);
        tag.put("Schematic", schematic.serializeNbt(registries));
        var list = new ListTag();
        for(float t : temperatures)
            list.add(FloatTag.valueOf(t));
        tag.put("Temperatures", list);
        tag.putIntArray("Burnt", burntIndices);
        tag.putInt("Light", light);
        return tag;
    }

    public static boolean measureSync;
    public final long[] syncStats = new long[4];

    private static int encodedSize(Level level, BoardSchematicPayload full, BoardStatePayload state) {
        var buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        try {
            if(full != null)
                BoardSchematicPayload.CODEC.encode(buf, full);
            else
                BoardStatePayload.CODEC.encode(buf, state);
            return buf.readableBytes();
        } finally {
            buf.release();
        }
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
        var full = device.fullTag();
        if(full != null && device.schematicVersion() != be.version) {
            int oldTerminals = be.terminals.size();
            be.applyFull(full, level.registryAccess());
            be.setChanged();
            var schematicPayload = new BoardSchematicPayload(pos, full);
            if(measureSync) {
                be.syncStats[0]++;
                be.syncStats[1] += encodedSize(level, schematicPayload, null);
            }
            PacketDistributor.sendToPlayersTrackingChunk(server, new ChunkPos(pos), schematicPayload);
            if(oldTerminals != be.terminals.size() || device.registeredPins != be.terminals.size())
                level.scheduleTick(pos, state.getBlock(), 1);
        }
        BoardStatePayload payload;
        while((payload = device.pollState()) != null) {
            if(be.applyState(payload, level.registryAccess())) {
                be.setChanged();
            }
            if(measureSync) {
                be.syncStats[2]++;
                be.syncStats[3] += encodedSize(level, null, payload);
            }
            PacketDistributor.sendToPlayersTrackingChunk(server, new ChunkPos(pos), payload);
        }
        if(state.getValue(BoardBlock.LIGHT) != be.light)
            level.setBlock(pos, state.setValue(BoardBlock.LIGHT, be.light), Block.UPDATE_CLIENTS);
        if(device.takeRedstoneDirty())
            level.updateNeighborsAt(pos, state.getBlock());
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, BoardBlockEntity be) {
        if(be.baked != null)
            be.baked.clientTick();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        var board = new CompoundTag();
        board.put("Schematic", schematic.serializeSafeNbt(registries));
        tag.put("Board", board);
        if(pendingSchematic != null)
            tag.put("Pending", pendingSchematic.copy());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if(tag.contains("Client")) {
            applyFull(tag.getCompound("Client"), registries);
            return;
        }
        if(tag.contains("Pending"))
            pendingSchematic = tag.getCompound("Pending");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.put("Client", writeClient(registries));
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void sendToMenu(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeNbt(writeClient(buf.registryAccess()));
    }

    public void readClient(CompoundTag tag, HolderLookup.Provider registries) {
        applyFull(tag, registries);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.voltworks.circuit_board")));
        if(schematic.getName() != null)
            tooltip.add(Component.literal("    ").append(Component.literal(schematic.getName()).withStyle(ChatFormatting.GRAY)));
        if(burnt > 0) {
            tooltip.add(Component.literal("    ").append(Component.translatable("voltworks.goggles.burnt", burnt).withStyle(ChatFormatting.RED)));
            tooltip.add(Component.literal("    ").append(Component.translatable("voltworks.gui.circuit_board.damage_body").withStyle(ChatFormatting.GRAY)));
        }
        var hovered = hoveredComponent();
        if(hovered != null) {
            if(hovered.component instanceof IComponentGoggleInformation info)
                info.addToGoggleTooltip(hovered, tooltip, isPlayerSneaking);
            if(isPlayerSneaking) {
                var item = ComponentRegistry.getItem(level, hovered.component);
                var line = Component.literal("    ").append(item.getDescription().copy().withStyle(ChatFormatting.GRAY));
                float t = partTemperature(hovered);
                if(Float.isFinite(t))
                    line.append(Component.literal(String.format(java.util.Locale.ROOT, " %.1f \u00b0C", t)).withStyle(ChatFormatting.DARK_GRAY));
                if(hovered.destroyed)
                    line.append(Component.literal(" ").append(Component.translatable("voltworks.goggles.part_burnt")).withStyle(ChatFormatting.RED));
                tooltip.add(line);
            }
        }
        if(isPlayerSneaking) {
            float max = Float.NEGATIVE_INFINITY;
            for(var t : temperatures)
                max = Math.max(max, t);
            if(Float.isFinite(max))
                tooltip.add(Component.literal("    ").append(Component.translatable("voltworks.goggles.hottest", String.format("%.1f", max)).withStyle(ChatFormatting.GRAY)));
        }
        return true;
    }

    private float partTemperature(PlacedComponent placed) {
        if(baked == null)
            return Float.NaN;
        for(var unit : baked.thermalUnits) {
            if(unit.getId().equals(placed.uuid))
                return unit.getTemperature();
        }
        return Float.NaN;
    }

    /** The part under the crosshair, same hit test as Power Grid's board goggles. Client only. */
    private @Nullable PlacedComponent hoveredComponent() {
        if(level == null || !level.isClientSide)
            return null;
        var hit = ClientHooks.hitLocation();
        if(hit == null)
            return null;
        var local = BoardBlock.unrotate(hit.subtract(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()),
                BoardBlock.getAngleX(getBlockState()), BoardBlock.getAngleY(getBlockState()));
        if(local.x < -0.01 || local.x > 1.01 || local.z < -0.01 || local.z > 1.01)
            return null;
        double px = local.x * 16, py = local.z * 16;
        PlacedComponent best = null;
        for(var placed : schematic.components()) {
            var footprint = placed.footprint();
            if(px >= placed.x && py >= placed.y && px < placed.x + footprint.getWidth() && py < placed.y + footprint.getHeight()) {
                if(best == null || placed.component instanceof IComponentGoggleInformation)
                    best = placed;
            }
        }
        return best;
    }

    public @Nullable PlacedComponent component(UUID uuid) {
        return baked == null ? null : baked.get(uuid);
    }
}
