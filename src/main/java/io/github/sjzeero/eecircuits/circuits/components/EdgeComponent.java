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
package io.github.sjzeero.eecircuits.circuits.components;

import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.eecircuits.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.eecircuits.circuits.schematic.PlacedComponent;

public abstract class EdgeComponent extends OrientableComponent {
    public EdgeComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    public boolean canPlace(@NotNull PlacedComponent placed, int x, int y) {
        var footprint = footprint(placed);
        return switch(placed.get(ORIENTATION)) {
            case LEFT -> x == 0;
            case UP -> y == 0;
            case RIGHT -> x == 16 - footprint.getWidth();
            case DOWN -> y == 16 - footprint.getHeight();
        };
    }
}
