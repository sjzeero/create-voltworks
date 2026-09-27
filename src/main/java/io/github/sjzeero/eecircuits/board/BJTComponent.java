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

import io.github.sjzeero.eecircuits.pgsim.special.BJTWire;
import net.minecraft.util.Mth;

public class BJTComponent implements BoardComponent {
    public static final String GAIN = "bjt_gain";

    private final boolean pnp;

    public BJTComponent(boolean pnp) {
        this.pnp = pnp;
    }

    @Override
    public int padCount() {
        return 3;
    }

    @Override
    public void bake(PlacedPart placed, BoardCircuitBuilder builder) {
        var wire = new BJTWire(
                builder.terminalNode(placed.pads[0]),
                builder.terminalNode(placed.pads[1]),
                builder.terminalNode(placed.pads[2]),
                5.47e-12, Mth.clamp(placed.getFloat(GAIN, 20), 5, 100), 0.1, pnp
        );
        builder.add(wire);
        placed.add(wire);
    }
}
