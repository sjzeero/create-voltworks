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
package io.github.sjzeero.eecircuits.circuits.components;

import com.google.common.collect.ImmutableCollection;
import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.eecircuits.EECircuits;
import io.github.sjzeero.eecircuits.circuits.circuitboard.ComponentCircuitBuilder;
import io.github.sjzeero.eecircuits.circuits.components.properties.BooleanProperty;
import io.github.sjzeero.eecircuits.circuits.components.properties.CalculatedProperty;
import io.github.sjzeero.eecircuits.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.eecircuits.circuits.components.properties.FloatProperty;
import io.github.sjzeero.eecircuits.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.eecircuits.circuits.schematic.PlacedComponent;
import io.github.sjzeero.eecircuits.circuits.thermal.ThermalBuilder;
import io.github.sjzeero.eecircuits.EECircuitsConfig;
import io.github.sjzeero.eecircuits.circuits.CircuitSounds;
import io.github.sjzeero.eecircuits.pgsim.special.RelaySwitchWire;
import io.github.sjzeero.eecircuits.circuits.Unit;

public class RelayComponent extends MirrorableComponent {
    public static final FloatProperty THRESHOLD_VOLTAGE = new FloatProperty(EECircuits.MOD_ID, "relay_threshold", 12, 1, 120);
    public static final BooleanProperty STATE = new BooleanProperty(EECircuits.MOD_ID, "relay_state").hidden().cast();
    public static final CalculatedProperty<Float> THRESHOLD_CURRENT = new CalculatedProperty<>(EECircuits.MOD_ID, "relay_current",
            c -> 1.2f / c.get(THRESHOLD_VOLTAGE),
            v -> Unit.CURRENT.formatWithPrefixes(v).string());
    public static final BooleanProperty POLARIZED = new BooleanProperty(EECircuits.MOD_ID, "relay_polarized");

    public RelayComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(THRESHOLD_VOLTAGE, STATE, THRESHOLD_CURRENT, POLARIZED, current(32));
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, ThermalBuilder.@NotNull IEmitter thermals) {
        // Target power is 1.2 W
        var onCurrent = placed.get(THRESHOLD_CURRENT);
        var offCurrent = onCurrent * EECircuitsConfig.holdingCurrentPercent();

        var resistance = placed.get(THRESHOLD_VOLTAGE) / onCurrent;
        var coilWire = builder.connect(resistance, builder.terminalNode(0), builder.terminalNode(1));

        final var switchResistance = 0.05f;
        var common = builder.terminalNode(3);
        var state = placed.get(STATE);
        var polarized = placed.get(POLARIZED);
        var normallyClosed = new RelaySwitchWire(switchResistance, common, builder.terminalNode(2), !state, coilWire, onCurrent, offCurrent, true, polarized);
        var normallyOpen = new RelaySwitchWire(switchResistance, common, builder.terminalNode(4), state, coilWire, onCurrent, offCurrent, false, polarized);

        builder.add(normallyClosed);
        builder.add(normallyOpen);

        placed.add(normallyClosed);
        placed.add(normallyOpen);

        thermals.builder()
                .setMaxCurrent(onCurrent * 2, resistance, 125f)
                .setThermalMass(0.02f)
                .addHeatSource(coilWire);
        thermals.builder()
                .setMaxCurrent(32, switchResistance, 125f)
                .setThermalMass(0.075f)
                .addHeatSource(normallyClosed)
                .addHeatSource(normallyOpen);
    }

    @Override
    public boolean tick(@NotNull PlacedComponent placed) {
        if(placed.wires.isEmpty())
            return true;

        var NC = (RelaySwitchWire) placed.wires.get(0);
        var NO = (RelaySwitchWire) placed.wires.get(1);
        if(NO.wasSwitched() | NC.wasSwitched()) {
            boolean state = NO.getState();
            placed.onServerWorld(() -> world -> CircuitSounds.RELAY_CLICK.playOnServer(
                    world,
                    placed.getPos(),
                    0.75f,
                    state ? 2.0f : 1.9f
            ));
            placed.set(STATE, state);
        }

        return true;
    }
}
