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
package io.github.sjzeero.eecircuits.circuits.circuitboard;

import io.github.sjzeero.eecircuits.circuits.Thermal;
import io.github.sjzeero.eecircuits.circuits.components.TerminalBoundingBox;
import io.github.sjzeero.eecircuits.circuits.schematic.BoardContext;
import io.github.sjzeero.eecircuits.circuits.schematic.CircuitSchematic;
import io.github.sjzeero.eecircuits.circuits.schematic.PlacedComponent;
import io.github.sjzeero.eecircuits.circuits.thermal.ThermalBuilder;
import io.github.sjzeero.eecircuits.circuits.thermal.ThermalUnit;
import io.github.sjzeero.eecircuits.pgsim.AbstractElectricWire;
import io.github.sjzeero.eecircuits.pgsim.ElectricWire;
import io.github.sjzeero.eecircuits.pgsim.node.FloatingNode;
import io.github.sjzeero.eecircuits.pgsim.node.INode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public class BakedCircuit {
    public final List<FloatingNode> externalNodes = new ArrayList<>();
    public final List<INode> internalNodes = new ArrayList<>();
    public final List<AbstractElectricWire> wires = new ArrayList<>();
    public final List<TerminalBoundingBox> terminals = new ArrayList<>();
    public final List<ThermalUnit> thermalUnits = new ArrayList<>();
    public final List<PlacedComponent> components = new ArrayList<>();
    public final List<PlacedComponent> tickedComponents = new ArrayList<>();
    private final Map<PlacedComponent, Function<Integer, FloatingNode>> padNodeProviderMap = new HashMap<>();
    private final Map<UUID, PlacedComponent> byUuid = new HashMap<>();

    private boolean firstTick = true;
    private float ambientTemperature = Thermal.STANDARD_TEMPERATURE;

    public FloatingNode getNode(CircuitSchematic.Node node) {
        return padNodeProviderMap.get(node.placed()).apply(node.pad());
    }

    private static void makePadNodes(BakedCircuit result, CircuitSchematic schematic, @Nullable BoardContext context) {
        for(var placed : schematic.components()) {
            placed.withContext(context);
            result.components.add(placed);
            result.byUuid.put(placed.uuid, placed);
            int maxIndex = -1;
            for(var pad : placed.footprint().getPads().values()) {
                if(pad.nodeIndex() >= 0)
                    maxIndex = Math.max(maxIndex, pad.nodeIndex());
            }
            int nodeCount = maxIndex + 1;
            var nodes = new FloatingNode[Math.max(nodeCount, 0)];
            boolean anyExternal = false;
            for(var i = 0; i < nodeCount; ++i) {
                var node = new FloatingNode();
                if(placed.component.isExternalNode(i)) {
                    result.externalNodes.add(node);
                    anyExternal = true;
                } else {
                    result.internalNodes.add(node);
                }
                nodes[i] = node;
            }
            Function<Integer, FloatingNode> provider = index -> nodes[index];
            result.padNodeProviderMap.put(placed, provider);

            var builder = new ComponentCircuitBuilder(provider, result.internalNodes, result.wires);
            placed.nodes.clear();
            placed.wires.clear();

            MutableInt thermalIndex = new MutableInt(0);
            var thermalBuilders = new ArrayList<ThermalBuilder>();
            ThermalBuilder.IEmitter thermalEmitter = () -> {
                var thermalBuilder = new ThermalBuilder(placed.getUUID(), thermalIndex.getAndIncrement());
                thermalBuilders.add(thermalBuilder);
                return thermalBuilder;
            };
            placed.destroyed = false;
            placed.component.bake(placed, builder, thermalEmitter);
            var footprint = placed.footprint();
            var local = new Vec3((placed.x + footprint.getWidth() * 0.5f) / 16f, 2 / 16f, (placed.y + footprint.getHeight() * 0.5f) / 16f);
            var position = context == null ? local : context.toWorld(local);
            thermalBuilders.stream()
                    .map(b -> b.build().withPosition(position))
                    .forEach(result.thermalUnits::add);
            result.tickedComponents.add(placed);

            if(anyExternal) {
                var bbs = placed.component.terminals(placed);
                bbs.stream().map(bb -> bb.offset(placed.x / 16f, 2 / 16f, placed.y / 16f)).forEach(result.terminals::add);
            }
        }
    }

    public static BakedCircuit from(CircuitSchematic schematic, @Nullable BoardContext context) {
        var result = new BakedCircuit();
        makePadNodes(result, schematic, context);

        var bundles = schematic.findNodeBundles();
        for(var bundle : bundles) {
            if(bundle.size() <= 1)
                continue;
            if(bundle.size() == 2) {
                var iter = bundle.iterator();
                var node1 = iter.next();
                var node2 = iter.next();

                var R = node1.getPadResistance() + node2.getPadResistance();
                var wire = new ElectricWire(R, result.getNode(node1), result.getNode(node2));
                result.wires.add(wire);
            } else {
                var junctionNode = new FloatingNode();
                result.internalNodes.add(junctionNode);

                for(var node : bundle) {
                    var wire = new ElectricWire(node.getPadResistance(), result.getNode(node), junctionNode);
                    result.wires.add(wire);
                }
            }
        }
        return result;
    }

    public @Nullable PlacedComponent get(UUID uuid) {
        return byUuid.get(uuid);
    }

    public void write(CompoundTag tag) {
        var thermalTag = new CompoundTag();
        for(var unit : thermalUnits) {
            unit.write(thermalTag);
        }
        tag.put("Thermal", thermalTag);
    }

    public void read(CompoundTag tag) {
        if(!tag.contains("Thermal"))
            return;
        var thermalTag = tag.getCompound("Thermal");
        for(var unit : thermalUnits) {
            if(!thermalTag.contains(unit.getKey()))
                continue;
            unit.read(thermalTag);
            if(unit.getTemperature() > Thermal.ABSOLUTE_ZERO)
                firstTick = false;
            var placed = byUuid.get(unit.getId());
            if(placed != null && unit.hasOverheated())
                placed.destroyed = true;
        }
    }

    public boolean isDamaged() {
        for(var unit : thermalUnits) {
            if(unit.hasOverheated())
                return true;
        }
        return false;
    }

    public void setAmbientTemperature(float ambient) {
        this.ambientTemperature = ambient;
        for(var unit : thermalUnits) {
            unit.setAmbientTemperature(ambient);
            unit.setTemperature(unit.getTemperature());
        }
    }

    public List<ThermalUnit> tick(float coolingMultiplier) {
        List<ThermalUnit> burnt = new ArrayList<>();
        if(firstTick) {
            firstTick = false;
            for(var unit : thermalUnits)
                unit.setAmbientTemperature(ambientTemperature);
        } else {
            for(var unit : thermalUnits)
                unit.setAmbientTemperature(ambientTemperature);
            if(Thermal.shouldOverheat()) {
                for(var unit : thermalUnits) {
                    var overheated = unit.hasOverheated();
                    unit.tick(coolingMultiplier);
                    if(!overheated && unit.hasOverheated()) {
                        burnt.add(unit);
                        var placed = byUuid.get(unit.getId());
                        if(placed != null)
                            placed.destroyed = true;
                    }
                }
            }
        }
        tickedComponents.removeIf(placed -> !placed.tick());
        return burnt;
    }
}
