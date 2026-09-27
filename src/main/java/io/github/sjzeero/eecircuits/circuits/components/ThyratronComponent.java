/*
 * Copyright 2026 patryk3211
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
package io.github.sjzeero.eecircuits.circuits.components;

import com.google.common.collect.ImmutableCollection;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.eecircuits.EECircuits;
import io.github.sjzeero.eecircuits.circuits.circuitboard.ComponentCircuitBuilder;
import io.github.sjzeero.eecircuits.circuits.components.properties.CalculatedProperty;
import io.github.sjzeero.eecircuits.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.eecircuits.circuits.components.properties.FloatProperty;
import io.github.sjzeero.eecircuits.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.eecircuits.circuits.schematic.PlacedComponent;
import io.github.sjzeero.eecircuits.circuits.thermal.ThermalBuilder;
import io.github.sjzeero.eecircuits.circuits.CircuitSounds;
import io.github.sjzeero.eecircuits.circuits.Thermal;
import io.github.sjzeero.eecircuits.pgsim.special.ThyratronWire;
import io.github.sjzeero.eecircuits.circuits.Unit;

import java.util.List;

import static io.github.sjzeero.eecircuits.circuits.components.NeonBulbComponent.LIT;

public class ThyratronComponent extends MirrorableComponent {
    public static final FloatProperty CONTROL_RATIO = new FloatProperty(EECircuits.MOD_ID, "thyratron_mu", 20, 5, 1000);
    public static final FloatProperty BREAKDOWN_VOLTAGE = new FloatProperty(EECircuits.MOD_ID, "thyratron_vb", 60, 20, 2000);
    public static final CalculatedProperty<Float> HOLDING_VOLTAGE = new CalculatedProperty<>(EECircuits.MOD_ID, "thyratron_vh",
            placed -> Mth.clamp(0.2f * placed.get(BREAKDOWN_VOLTAGE), 8f, 16f),
            v -> Unit.VOLTAGE.formatWithPrefixes(v).string());
    public static final CalculatedProperty<Float> HOLDING_CURRENT = new CalculatedProperty<>(EECircuits.MOD_ID, "thyratron_ih",
            placed -> 0.5f / placed.get(BREAKDOWN_VOLTAGE),
            v -> Unit.CURRENT.formatWithPrefixes(v).string());
    public static final FloatProperty HEATER_VOLTAGE = new FloatProperty(EECircuits.MOD_ID, "tube_heater_voltage", 6f, 1f, 16f);
    public static final CalculatedProperty<Float> HEATER_POWER = new CalculatedProperty<>(EECircuits.MOD_ID, "tube_heater_power",
            state -> 150f,
            value -> String.format("%.1f W", value));

    private static final float DISCHARGE_CONDUCTANCE = 50f;
    private static final float ANODE_POWER = 15_000f;
    private static final float GRID_LEAK_RESISTANCE = 1_000_000f;
    private static final int ANODE_NODE = 2;
    private static final List<TerminalBoundingBox> ANODE_TERMINAL = List.of(
            new TerminalBoundingBox(
                    net.minecraft.network.chat.Component.translatable("component.eecircuits.thyratron.2"),
                    0.5f, 10.5f, 0.5f, 2.5f, 12.5f, 2.5f
            ).withColor(TerminalBoundingBox.RED)
    );

    public ThyratronComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(CONTROL_RATIO, BREAKDOWN_VOLTAGE, HOLDING_VOLTAGE, HOLDING_CURRENT, HEATER_VOLTAGE, HEATER_POWER, LIT, power(ANODE_POWER));
    }

    @Override
    public boolean isExternalNode(int nodeIndex) {
        return nodeIndex == ANODE_NODE;
    }

    @Override
    public List<TerminalBoundingBox> terminals(@NotNull PlacedComponent placed) {
        return ANODE_TERMINAL;
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, ThermalBuilder.@NotNull IEmitter thermals) {
        var cathode = builder.terminalNode(0);
        var grid = builder.terminalNode(1);
        var anode = builder.terminalNode(2);

        var tube = new ThyratronWire(
                placed.get(CONTROL_RATIO),
                placed.get(BREAKDOWN_VOLTAGE),
                placed.get(HOLDING_VOLTAGE),
                placed.get(HOLDING_CURRENT),
                DISCHARGE_CONDUCTANCE,
                cathode, anode, grid
        );
        tube.setLit(placed.get(LIT));
        tube.setEmission(0);
        builder.add(tube);
        builder.connect(GRID_LEAK_RESISTANCE, grid, cathode);

        var targetPower = placed.get(HEATER_POWER);
        var heaterCurrent = targetPower / placed.get(HEATER_VOLTAGE);
        var heaterResistance = placed.get(HEATER_VOLTAGE) / heaterCurrent;
        var heater = builder.connect(heaterResistance, builder.terminalNode(3), builder.terminalNode(4));

        placed.add(tube);
        placed.add(heater);

        var data = new RenderData();
        placed.customData = data;

        final var operatingTemperature = 1400f;
        final var dissipationFactor = Thermal.dissipationFactor(targetPower, operatingTemperature);
        thermals.builder()
                .addHeatSource(heater)
                .setThermalMass(0.001f * targetPower / 5f)
                .setOverheatTemperature(1600f)
                .setDissipationFactor(dissipationFactor)
                .withTemperatureCallback(temperature -> {
                    tube.setEmission(Mth.clamp((temperature - 1300f) / 100f, 0, 1));
                    data.heaterPrev = data.heater;
                    data.heater = Mth.clamp((temperature - 1000) / 400f, 0, 1.125f);
                });

        thermals.builder()
                .addHeatSource(tube)
                .setThermalMass(0.05f * ANODE_POWER / 25f)
                .setMaxPower(ANODE_POWER, 125f);
    }

    @Override
    public boolean tick(@NotNull PlacedComponent placed) {
        if (placed.wires.isEmpty())
            return true;
        var wire = (ThyratronWire) placed.wires.get(0);
        placed.onServerWorld(() -> world -> {
            if (wire.isLit() != placed.get(LIT)) {
                if (wire.isLit()) {
                    CircuitSounds.THYRATRON_FIRE.playOnServer(world, placed.getPos(), 0.8f, 0.95f + world.random.nextFloat() * 0.2f);
                }
                placed.set(LIT, wire.isLit());
                placed.notifyClients(LIT);
            }
        });
        return true;
    }

    @Override
    public void stateUpdated(@NotNull PlacedComponent placed) {
        if (placed.wires.isEmpty())
            return;
        var wire = (ThyratronWire) placed.wires.get(0);
        wire.setLit(placed.get(LIT));
    }

    private static class RenderData {
        float heaterPrev;
        float heater;
    }
}
