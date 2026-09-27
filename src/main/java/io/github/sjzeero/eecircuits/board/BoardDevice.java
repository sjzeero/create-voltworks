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
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import io.github.sjzeero.eecircuits.circuits.components.properties.ConstantProperty;
import io.github.sjzeero.eecircuits.network.BoardStatePayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import io.github.sjzeero.eecircuits.circuits.components.ComponentRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class BoardDevice extends SimpleElectricalDevice implements BoardContext {
    public static final int SYNC_INTERVAL = 5;
    public static final float TEMPERATURE_STEP = 0.5f;

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

    private volatile CompoundTag fullTag;
    private volatile int schematicVersion;
    private final ConcurrentLinkedQueue<BoardStatePayload> outbox = new ConcurrentLinkedQueue<>();
    private final Map<String, CompoundTag> sentProperties = new HashMap<>();
    private float[] sentTemperatures = new float[0];
    private int[] sentBurnt = new int[0];
    private int sentLight;
    private volatile int[] emitted = new int[6];
    private volatile boolean redstoneDirty;
    private volatile Readings readings = Readings.EMPTY;
    public static final int LINK_NODE_BASE = 1000;
    private boolean hasLinkVias;
    private long ownLinksTick = Long.MIN_VALUE;
    private List<BoardLinks.Link> ownLinks = List.of();
    private final Map<com.simibubi.create.content.kinetics.fan.AirCurrent, Float> coolingAir = new HashMap<>();
    private float coolingMultiplier = 1.0f;

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

    public void queuePropertyUpdate(UUID component, ResourceLocation propertyId, CompoundTag value) {
        var copy = value.copy();
        queue(device -> {
            if(device.baked == null)
                return;
            var placed = device.baked.get(component);
            if(placed == null)
                return;
            try {
                var entry = placed.getEntry(propertyId);
                if(entry.property.isHidden())
                    return;
                entry.read(level.registryAccess(), copy);
            } catch(IllegalArgumentException e) {
                return;
            }
            placed.stateUpdated();
            device.changed = true;
        });
    }

    public void queueRepair() {
        queue(device -> {
            device.savedState = new CompoundTag();
            device.rebuild = true;
        });
    }

    public void addCoolingMultiplier(com.simibubi.create.content.kinetics.fan.AirCurrent current, float value) {
        var previous = coolingAir.put(current, value);
        if(previous != null)
            coolingMultiplier = Math.max(1, coolingMultiplier - previous);
        coolingMultiplier += value;
    }

    public void removeCoolingMultiplier(com.simibubi.create.content.kinetics.fan.AirCurrent current) {
        var previous = coolingAir.remove(current);
        if(previous != null)
            coolingMultiplier = Math.max(1, coolingMultiplier - previous);
    }

    public float coolingMultiplier() {
        return coolingMultiplier;
    }

    private void updateCooling() {
        if(coolingAir.isEmpty())
            return;
        var iter = coolingAir.entrySet().iterator();
        while(iter.hasNext()) {
            var entry = iter.next();
            var source = entry.getKey().source;
            if(source.isSourceRemoved() || source.getSpeed() == 0) {
                coolingMultiplier = Math.max(1, coolingMultiplier - entry.getValue());
                iter.remove();
            }
        }
        if(coolingAir.isEmpty())
            coolingMultiplier = 1.0f;
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

    public @Nullable CompoundTag fullTag() {
        return fullTag;
    }

    public int schematicVersion() {
        return schematicVersion;
    }

    public @Nullable BoardStatePayload pollState() {
        return outbox.poll();
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
        hasLinkVias = baked.components.stream().anyMatch(BoardLinks::canLink);
        ownLinksTick = Long.MIN_VALUE;
        sim = new BoardSim(baked, previous);
        baked.read(state);
        rebuild = false;
        changed = true;
        redstoneDirty = true;
        publishFull();
    }

    private void publishFull() {
        var tag = new CompoundTag();
        tag.put("Schematic", schematic.serializeNbt(level.registryAccess()));
        sentProperties.clear();
        for(int i = 0; i < baked.components.size(); i++) {
            var placed = baked.components.get(i);
            for(var property : placed.component.getProperties()) {
                if(property instanceof ConstantProperty)
                    continue;
                var value = new CompoundTag();
                placed.getEntry(property).write(level.registryAccess(), value);
                sentProperties.put(i + "|" + property.id(), value);
            }
        }
        sentTemperatures = temperatures();
        sentBurnt = burntIndices();
        sentLight = lightLevel();
        tag.put("Temperatures", floatList(sentTemperatures));
        tag.putIntArray("Burnt", sentBurnt);
        tag.putInt("Light", sentLight);
        outbox.clear();
        schematicVersion++;
        tag.putInt("Version", schematicVersion);
        fullTag = tag;
    }

    private static ListTag floatList(float[] values) {
        var list = new ListTag();
        for(float v : values)
            list.add(FloatTag.valueOf(v));
        return list;
    }

    private float[] temperatures() {
        var temps = new float[baked.thermalUnits.size()];
        for(int i = 0; i < temps.length; i++)
            temps[i] = baked.thermalUnits.get(i).getTemperature();
        return temps;
    }

    private int[] burntIndices() {
        var list = new ArrayList<Integer>();
        for(int i = 0; i < baked.components.size(); i++)
            if(baked.components.get(i).destroyed)
                list.add(i);
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    private void publishState() {
        var changes = new ArrayList<BoardStatePayload.Change>();
        for(int i = 0; i < baked.components.size(); i++) {
            var placed = baked.components.get(i);
            for(var property : placed.component.getProperties()) {
                if(property instanceof ConstantProperty)
                    continue;
                var value = new CompoundTag();
                placed.getEntry(property).write(level.registryAccess(), value);
                var key = i + "|" + property.id();
                if(!value.equals(sentProperties.get(key))) {
                    sentProperties.put(key, value);
                    changes.add(new BoardStatePayload.Change(i, property.id(), value));
                }
            }
        }
        var temps = temperatures();
        boolean tempsChanged = temps.length != sentTemperatures.length;
        for(int i = 0; !tempsChanged && i < temps.length; i++)
            tempsChanged = Math.abs(temps[i] - sentTemperatures[i]) > TEMPERATURE_STEP;
        var burnt = burntIndices();
        boolean burntChanged = !Arrays.equals(burnt, sentBurnt);
        int light = lightLevel();
        if(changes.isEmpty() && !tempsChanged && !burntChanged && light == sentLight)
            return;
        if(tempsChanged)
            sentTemperatures = temps;
        if(burntChanged)
            sentBurnt = burnt;
        sentLight = light;
        outbox.add(new BoardStatePayload(pos, schematicVersion, changes, tempsChanged ? temps : null, burntChanged ? burnt : null, light));
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
        List<BoardLinks.Link> links = List.of();
        if(level instanceof ServerLevel server) {
            links = allLinks(server);
            var vias = linkedVias(links);
            if(!vias.equals(sim.linkVias))
                sim = new BoardSim(baked, sim.pinU(), vias);
        }
        sim.serial = ++serial;
        if(!sim.coupled() || registeredPins < sim.connectorPins)
            return;
        int pins = sim.pins;
        for(int k = 1; k < pins; k++) {
            bridges.bridge(pinNode(k), pinNode(0), new PinBranch(sim, k, 0, serial));
            for(int j = k + 1; j < pins; j++)
                bridges.bridge(pinNode(k), pinNode(j), new PinBranch(sim, k, j, serial));
        }
        for(var link : links) {
            if(pos.compareTo(link.other().pos()) >= 0)
                continue;
            var otherSim = link.other().sim;
            if(otherSim == null || !otherSim.linkVias.contains(link.theirs()) || !sim.linkVias.contains(link.mine()))
                continue;
            bridges.bridge(linkNode(link.mine()), link.other().linkNode(link.theirs()),
                    com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties.resistor(BoardLinks.LINK_RESISTANCE));
        }
    }

    private InWorldNode pinNode(int k) {
        if(k < sim.connectorPins)
            return new InWorldNode(k, pos);
        return linkNode(sim.linkVias.get(k - sim.connectorPins));
    }

    InWorldNode linkNode(PlacedComponent via) {
        return new InWorldNode(LINK_NODE_BASE + baked.components.indexOf(via), pos);
    }

    boolean hasLinkVias() {
        return hasLinkVias;
    }

    private List<BoardLinks.Link> ownLinks(ServerLevel server) {
        long now = server.getGameTime();
        if(ownLinksTick != now) {
            ownLinksTick = now;
            ownLinks = hasLinkVias ? BoardLinks.find(this, server) : List.of();
        }
        return ownLinks;
    }

    /** Links found from this board plus the ones neighbours found towards it, so both sides agree. */
    public List<BoardLinks.Link> allLinks(ServerLevel server) {
        if(!hasLinkVias || baked == null)
            return List.of();
        var result = new ArrayList<>(ownLinks(server));
        var devices = DevicesSavedData.load(server);
        for(var dir : Direction.values()) {
            var npos = pos.relative(dir);
            if(!server.isLoaded(npos) || !(server.getBlockState(npos).getBlock() instanceof BoardBlock))
                continue;
            var other = devices.getDevice(npos, BoardDevice.class);
            if(other == null || other.baked == null || !other.hasLinkVias)
                continue;
            for(var link : other.ownLinks(server)) {
                if(link.other() != this)
                    continue;
                boolean known = false;
                for(var existing : result)
                    known |= existing.other() == other && existing.mine() == link.theirs() && existing.theirs() == link.mine();
                if(!known)
                    result.add(new BoardLinks.Link(link.theirs(), other, link.mine()));
            }
        }
        return result;
    }

    private List<PlacedComponent> linkedVias(List<BoardLinks.Link> links) {
        if(links.isEmpty())
            return List.of();
        var vias = new ArrayList<PlacedComponent>();
        for(var placed : baked.components) {
            for(var link : links) {
                if(link.mine() == placed) {
                    vias.add(placed);
                    break;
                }
            }
        }
        return vias;
    }

    @Override
    public void postTick(SimulationResults results) {
        if(sim == null || baked == null)
            return;
        if(!sim.coupled() || registeredPins < sim.connectorPins)
            sim.standaloneTick(CEEConfigs.server().simulationConfig.microTicks.get());
        updateCooling();
        var burnt = baked.tick(coolingMultiplier);
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
        readings = new Readings(level.getGameTime(), u, i, summarize(), sim.converged, sim.badSteps);

        long now = level.getGameTime();
        if(changed || now - lastSync >= SYNC_INTERVAL) {
            lastSync = now;
            changed = false;
            publishState();
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
