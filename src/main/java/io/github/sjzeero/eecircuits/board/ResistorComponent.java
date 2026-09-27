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

import net.minecraft.util.Mth;

public class ResistorComponent implements BoardComponent {
    public static final String RESISTANCE = "resistor_value";

    @Override
    public int padCount() {
        return 2;
    }

    @Override
    public void bake(PlacedPart placed, BoardCircuitBuilder builder) {
        float resistance = Mth.clamp(placed.getFloat(RESISTANCE, 100f), 0.1f, 100_000_000f);
        placed.add(builder.connect(resistance, builder.terminalNode(placed.pads[0]), builder.terminalNode(placed.pads[1])));
    }
}
