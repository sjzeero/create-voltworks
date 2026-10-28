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
package io.github.sjzeero.voltworks.circuits.components;

import com.google.common.collect.ImmutableCollection;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.circuitboard.ComponentCircuitBuilder;
import io.github.sjzeero.voltworks.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.FloatProperty;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import io.github.sjzeero.voltworks.circuits.thermal.ThermalBuilder;
import io.github.sjzeero.voltworks.sim.special.CRSeriesWire;

public class CapacitorComponent extends OrientableComponent {
    public static final FloatProperty CAPACITANCE = new FloatProperty(Voltworks.MOD_ID, "capacitor_value", 0.0001f, 1e-12f, 1.0f);
    private static final ChargeProperty CHARGE = new ChargeProperty(Voltworks.MOD_ID, "charge");

    public CapacitorComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(CAPACITANCE, CHARGE);
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, ThermalBuilder.@NotNull IEmitter thermals) {
        var capacitorWire = new CRSeriesWire(placed.get(CAPACITANCE), 0.01f, builder.terminalNode(0), builder.terminalNode(1));
        capacitorWire.setVoltage(placed.get(CHARGE));
        builder.add(capacitorWire);
        placed.add(capacitorWire);
    }

    @Override
    public boolean tick(@NotNull PlacedComponent placed) {
        if(!placed.wires.isEmpty()) {
            placed.set(CHARGE, (float) ((CRSeriesWire) placed.wires.get(0)).capacitorVoltage());
        }
        return true;
    }

    @Override
    public void stateUpdated(@NotNull PlacedComponent placed) {
        if(placed.wires.isEmpty())
            return;
        var wire = (CRSeriesWire) placed.wires.get(0);
        wire.setVoltage(placed.get(CHARGE));
    }

    @Override
    public void dataFixup(@NotNull CompoundTag tag, int version) {
        if(version == 1) {
            var props = tag.getCompound("Properties");
            props.putFloat(CAPACITANCE.id().toString(), props.getFloat(CAPACITANCE.id().toString()) / 1000);
        }
    }

    private static class ChargeProperty extends FloatProperty {
        public ChargeProperty(String namespace, String name) {
            super(namespace, name, 0, 0, 0);
        }

        @Override
        public Float parse(String str) throws NumberFormatException {
            return 0.0f;
        }

        @Override
        public boolean isHidden() {
            return true;
        }

        @Override
        public boolean isUnsafe() {
            return true;
        }

        @Override
        protected float limit(float value) {
            return value;
        }
    }
}
