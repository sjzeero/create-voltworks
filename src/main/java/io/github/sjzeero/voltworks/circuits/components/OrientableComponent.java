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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import io.github.sjzeero.voltworks.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.EnumProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.Orientation;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import io.github.sjzeero.voltworks.Voltworks;

public abstract class OrientableComponent extends Component {
    public static final EnumProperty<Orientation> ORIENTATION = Orientation.PROPERTY;

    public OrientableComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(ORIENTATION);
    }

    @Override
    public ComponentFootprint footprint(@Nullable PlacedComponent placed) {
        var footprint = super.footprint(placed);
        if(placed == null)
            return footprint;
        return footprint.rotated(placed.get(ORIENTATION));
    }

    public boolean rotate(@NotNull PlacedComponent placed, boolean counterClockwise) {
        var orientation = placed.get(Orientation.PROPERTY);
        orientation = counterClockwise ? orientation.getCounterClockwise() : orientation.getClockwise();
        placed.set(Orientation.PROPERTY, orientation);
        return true;
    }
}
