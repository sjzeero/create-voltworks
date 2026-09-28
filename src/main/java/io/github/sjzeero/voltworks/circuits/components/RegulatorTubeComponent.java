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
package io.github.sjzeero.voltworks.circuits.components;

import com.google.common.collect.ImmutableCollection;
import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.circuitboard.ComponentCircuitBuilder;
import io.github.sjzeero.voltworks.circuits.components.properties.CalculatedProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.FloatProperty;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import io.github.sjzeero.voltworks.circuits.thermal.ThermalBuilder;
import io.github.sjzeero.voltworks.pgsim.special.ColdCathodeRegulatorTubeWire;
import io.github.sjzeero.voltworks.circuits.Unit;

import static io.github.sjzeero.voltworks.circuits.components.NeonBulbComponent.LIT;

public class RegulatorTubeComponent extends OrientableComponent {
    public static final FloatProperty HOLDING_VOLTAGE = new FloatProperty(Voltworks.MOD_ID, "regulator_tube_vh", 60, 30, 500);
    public static final CalculatedProperty<Float> BREAKDOWN_VOLTAGE = new CalculatedProperty<>(Voltworks.MOD_ID, "neon_tube_vb",
            placed -> placed.get(HOLDING_VOLTAGE) * 1.4f,
            v -> Unit.VOLTAGE.format(v).getString());
    public static final CalculatedProperty<Float> HOLDING_CURRENT = new CalculatedProperty<>(Voltworks.MOD_ID, "neon_tube_ih",
            placed -> 0.2f / placed.get(HOLDING_VOLTAGE),
            v -> Unit.CURRENT.formatWithPrefixes(v).string());

    public RegulatorTubeComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(HOLDING_VOLTAGE, BREAKDOWN_VOLTAGE, HOLDING_CURRENT, LIT, power(1.5f));
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, ThermalBuilder.@NotNull IEmitter thermals) {
        var ih = placed.get(HOLDING_CURRENT);
        var wire = new ColdCathodeRegulatorTubeWire(
                placed.get(BREAKDOWN_VOLTAGE), placed.get(HOLDING_VOLTAGE), ih, 0.025f,
                builder.terminalNode(0), builder.terminalNode(1)
        );
        wire.setLit(placed.get(LIT));
        builder.add(wire);
        placed.add(wire);

        thermals.builder()
                .addHeatSource(wire)
                .setThermalMass(0.01f)
                .setMaxPower(1.5f, 125f);
    }

    @Override
    public boolean tick(@NotNull PlacedComponent placed) {
        if(placed.wires.isEmpty())
            return true;
        var wire = (ColdCathodeRegulatorTubeWire) placed.wires.get(0);
        placed.onServerWorld(() -> $ -> {
            if(wire.isLit() != placed.get(LIT)) {
                placed.set(LIT, wire.isLit());
                placed.notifyClients(LIT);
            }
        });
        return true;
    }

    @Override
    public void stateUpdated(@NotNull PlacedComponent placed) {
        if(placed.wires.isEmpty())
            return;
        var wire = (ColdCathodeRegulatorTubeWire) placed.wires.get(0);
        wire.setLit(placed.get(LIT));
    }
}
