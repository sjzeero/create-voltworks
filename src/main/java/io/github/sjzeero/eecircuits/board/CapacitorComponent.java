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

import io.github.sjzeero.eecircuits.pgsim.special.CRSeriesWire;
import net.minecraft.util.Mth;

public class CapacitorComponent implements BoardComponent {
    public static final String CAPACITANCE = "capacitor_value";
    public static final String CHARGE = "charge";

    @Override
    public int padCount() {
        return 2;
    }

    @Override
    public void bake(PlacedPart placed, BoardCircuitBuilder builder) {
        var capacitorWire = new CRSeriesWire(Mth.clamp(placed.getFloat(CAPACITANCE, 0.0001f), 1e-12f, 1.0f), 0.01f,
                builder.terminalNode(placed.pads[0]), builder.terminalNode(placed.pads[1]));
        capacitorWire.setVoltage(placed.getFloat(CHARGE, 0));
        builder.add(capacitorWire);
        placed.add(capacitorWire);
    }

    @Override
    public void save(PlacedPart placed) {
        if(!placed.wires.isEmpty())
            placed.properties.putFloat(CHARGE, (float) ((CRSeriesWire) placed.wires.get(0)).capacitorVoltage());
    }
}
