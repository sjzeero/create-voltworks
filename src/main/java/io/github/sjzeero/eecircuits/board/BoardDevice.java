package io.github.sjzeero.eecircuits.board;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import io.github.sjzeero.eecircuits.EECircuits;
import io.github.sjzeero.eecircuits.circuits.CircuitSounds;
import io.github.sjzeero.eecircuits.circuits.Thermal;
import io.github.sjzeero.eecircuits.circuits.circuitboard.BakedCircuit;
import io.github.sjzeero.eecircuits.circuits.components.IRedstoneComponent;
import io.github.sjzeero.eecircuits.circuits.components.properties.Orientation;
import io.github.sjzeero.eecircuits.circuits.schematic.BoardContext;
import io.github.sjzeero.eecircuits.circuits.schematic.CircuitSchematic;
import io.github.sjzeero.eecircuits.circuits.schematic.PlacedComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import io.github.sjzeero.eecircuits.circuits.components.ComponentRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class BoardDevice extends SimpleElectricalDevice implements BoardContext {
    public static final int SYNC_INTERVAL = 5;

    private CircuitSchematic schematic = new CircuitSchematic();
    private BakedCircuit baked;
    private BoardSim sim;
    private boolean rebuild = true;
    private CompoundTag savedState;
    private long serial;
    private final ConcurrentLinkedQueue<Consumer<BoardDevice>> actions = new ConcurrentLinkedQueue<>();

    volatile int registeredPins;
    private boolean changed = true;
    private long lastSync = -SYNC_INTERVAL;

    private volatile CompoundTag clientTag;
    private volatile int version;
    private volatile int[] emitted = new int[6];
    private volatile boolean redstoneDirty;
    private volatile Readings readings = Readings.EMPTY;

    public record Readings(long tick, double[] pinU, double[] pinI, List<String> parts, boolean converged, int badSteps) {
        static final Readings EMPTY = new Readings(0, new double[0], new double[0], List.of(), true, 0);
    }

    public BoardDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    @Override
    public Level level() {
        return level;
    }

    @Override
    public BlockPos pos() {
        return pos;
    }

    @Override
    public boolean isClient() {
        return false;
    }

    @Override
    public Vec3 toWorld(Vec3 local) {
        return BoardBlock.toWorld(level.getBlockState(pos), pos, local);
    }

    @Override
    public void propertyChanged(PlacedComponent component, ResourceLocation property) {
        changed = true;
    }

    @Override
    public void markChanged() {
        changed = true;
    }

    @Override
    public void redstoneChanged(PlacedComponent component) {
        redstoneDirty = true;
    }

    public void queue(Consumer<BoardDevice> action) {
        actions.add(action);
    }

    public void queueUse(UUID component, net.minecraft.world.entity.player.Player player) {
        queue(device -> {
            if(device.baked == null)
                return;
            var placed = device.baked.get(component);
            if(placed != null && !placed.destroyed && placed.component instanceof io.github.sjzeero.eecircuits.circuits.components.IInteractableComponent interactable)
                interactable.use(device, placed, player);
        });
    }

    public void queueRedstone(@Nullable Orientation side, int power) {
        queue(device -> {
            if(device.baked == null)
                return;
            for(var placed : device.baked.components) {
                if(!(placed.component instanceof IRedstoneComponent redstone) || !redstone.isReceiver())
                    continue;
                if(placed.has(Orientation.PROPERTY) && placed.get(Orientation.PROPERTY) != side)
                    continue;
                redstone.receiveRedstone(placed, power);
            }
        });
    }

    public void queueSchematic(CompoundTag tag) {
        var copy = tag.copy();
        queue(device -> {
            device.schematic = CircuitSchematic.fromNbt(level.registryAccess(), copy);
            device.savedState = new CompoundTag();
            device.sim = null;
            device.rebuild = true;
        });
    }

    public void queueRepair() {
        queue(device -> {
            device.savedState = new CompoundTag();
            device.rebuild = true;
        });
    }

    public @Nullable BakedCircuit baked() {
        return baked;
    }

    public CircuitSchematic schematic() {
        return schematic;
    }

    public Readings readings() {
        return readings;
    }

    public @Nullable CompoundTag clientTag() {
        return clientTag;
    }

    public int version() {
        return version;
    }

    public int emitted(Direction direction) {
        return emitted[direction.get3DDataValue()];
    }

    public boolean takeRedstoneDirty() {
        if(!redstoneDirty)
            return false;
        redstoneDirty = false;
        return true;
    }

    private void bake() {
        var previous = sim == null ? null : sim.pinU();
        var state = new CompoundTag();
        if(savedState != null) {
            state = savedState;
            savedState = null;
        } else if(baked != null) {
            baked.write(state);
        }
        baked = BakedCircuit.from(schematic, this);
        baked.setAmbientTemperature(Thermal.getAmbientTemperature(level, pos));
        sim = new BoardSim(baked, previous);
        baked.read(state);
        rebuild = false;
        changed = true;
        redstoneDirty = true;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        Consumer<BoardDevice> action;
        while((action = actions.poll()) != null) {
            if(rebuild)
                bake();
            action.accept(this);
        }
        if(rebuild || sim == null)
            bake();
        sim.serial = ++serial;
        int pins = Math.min(sim.pins, registeredPins);
        if(!sim.coupled() || pins < sim.pins)
            return;
        for(int k = 1; k < pins; k++) {
            bridges.bridge(new InWorldNode(k, pos), new InWorldNode(0, pos), new PinBranch(sim, k, 0, serial));
            for(int j = k + 1; j < pins; j++)
                bridges.bridge(new InWorldNode(k, pos), new InWorldNode(j, pos), new PinBranch(sim, k, j, serial));
        }
    }

    @Override
    public void postTick(SimulationResults results) {
        if(sim == null || baked == null)
            return;
        if(!sim.coupled() || Math.min(sim.pins, registeredPins) < sim.pins)
            sim.standaloneTick(CEEConfigs.server().simulationConfig.microTicks.get());
        var burnt = baked.tick(1.0f);
        if(!burnt.isEmpty() && level instanceof ServerLevel server) {
            for(var unit : burnt) {
                var p = unit.getPosition();
                server.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 8, 0.03, 0.03, 0.03, 0.02);
                CircuitSounds.COMPONENT_EXPLODE.playAt(level, p, 1.0f, level.random.nextFloat() * 0.1f + 0.9f);
            }
            changed = true;
        }
        if(level instanceof ServerLevel server && level.getGameTime() % 4 == 0) {
            for(var unit : baked.thermalUnits) {
                if(!unit.hasOverheated() && unit.getTemperature() >= unit.getOverheatTemperature() - 50f) {
                    var p = unit.getPosition();
                    server.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.01);
                }
            }
        }

        int[] signals = new int[6];
        var state = level.getBlockState(pos);
        if(state.getBlock() instanceof BoardBlock) {
            for(var placed : baked.components) {
                if(!(placed.component instanceof IRedstoneComponent redstone) || !redstone.isEmitter())
                    continue;
                int power = redstone.getEmittedLevel(placed);
                for(var dir : Direction.values()) {
                    if(placed.has(Orientation.PROPERTY) && placed.get(Orientation.PROPERTY) != BoardBlock.getOrientation(state, dir))
                        continue;
                    signals[dir.get3DDataValue()] = Math.max(signals[dir.get3DDataValue()], power);
                }
            }
        }
        emitted = signals;

        int n = sim.pins;
        double[] u = new double[n];
        double[] i = new double[n];
        for(int k = 0; k < n; k++) {
            u[k] = sim.pinU[k];
            i[k] = sim.pinCurrent(k);
        }
        int units = baked.thermalUnits.size();
        readings = new Readings(level.getGameTime(), u, i, summarize(), sim.converged, sim.badSteps);

        long now = level.getGameTime();
        if(now - lastSync >= SYNC_INTERVAL && (changed || units > 0)) {
            lastSync = now;
            changed = false;
            var tag = new CompoundTag();
            tag.put("Schematic", schematic.serializeNbt(level.registryAccess()));
            baked.write(tag);
            tag.putInt("Light", lightLevel());
            var burntList = new net.minecraft.nbt.ListTag();
            for(var placed : baked.components)
                if(placed.destroyed)
                    burntList.add(net.minecraft.nbt.StringTag.valueOf(placed.uuid.toString()));
            tag.put("Burnt", burntList);
            clientTag = tag;
            version++;
        }
    }

    private List<String> summarize() {
        List<String> parts = new ArrayList<>();
        for(var placed : baked.components) {
            var id = ComponentRegistry.getId(placed.component).getPath();
            if(id.equals("connector"))
                continue;
            var sb = new StringBuilder(id).append('@').append(placed.x).append(',').append(placed.y);
            for(int w = 0; w < placed.wires.size(); w++) {
                var wire = placed.wires.get(w);
                sb.append(String.format(Locale.ROOT, " w%d[I=%.5g dV=%.5g]", w, wire.current(), wire.potentialDifference()));
            }
            for(var property : placed.component.getProperties()) {
                if(property.id().getPath().equals("label") || property.id().getPath().equals("color"))
                    continue;
                if(property instanceof io.github.sjzeero.eecircuits.circuits.components.properties.ConstantProperty)
                    continue;
                sb.append(' ').append(property.id().getPath()).append('=').append(placed.getString(property));
            }
            for(var unit : baked.thermalUnits) {
                if(unit.getId().equals(placed.uuid))
                    sb.append(String.format(Locale.ROOT, " T=%.2f", unit.getTemperature()));
            }
            if(placed.destroyed)
                sb.append(" BURNT");
            parts.add(sb.toString());
        }
        return parts;
    }

    private int lightLevel() {
        int light = 0;
        for(var placed : baked.components) {
            if(placed.destroyed)
                continue;
            light = Math.max(light, BoardLights.lightOf(placed));
        }
        return light;
    }

    @Override
    public boolean shouldRemove(net.minecraft.world.level.block.state.BlockState oldState, net.minecraft.world.level.block.state.BlockState newState) {
        return !(newState.getBlock() instanceof BoardBlock);
    }

    @Override
    public void read(CompoundTag tag) {
        if(tag.contains("Schematic")) {
            schematic = CircuitSchematic.fromNbt(level.registryAccess(), tag.getCompound("Schematic"));
            savedState = tag.getCompound("State");
            rebuild = true;
        }
    }

    @Override
    public void write(CompoundTag tag) {
        tag.put("Schematic", schematic.serializeNbt(level.registryAccess()));
        var state = new CompoundTag();
        if(baked != null)
            baked.write(state);
        else if(savedState != null)
            state = savedState;
        tag.put("State", state);
    }
}
