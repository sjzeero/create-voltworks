/*
 * Copyright 2025-2026 Create: Power Grid contributors (https://github.com/patryk3211/PowerGrid)
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
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.components.properties.BooleanProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;

public abstract class MirrorableComponent extends OrientableComponent {
    public static final BooleanProperty MIRRORED = new BooleanProperty(Voltworks.MOD_ID, "mirrored").hidden().cast();

    protected final ComponentFootprint mirroredFootprint;

    public MirrorableComponent(ComponentFootprint footprint) {
        this(footprint, footprint.mirroredX());
    }

    public MirrorableComponent(ComponentFootprint footprint, ComponentFootprint mirrored) {
        super(footprint);
        mirroredFootprint = mirrored;
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(MIRRORED);
    }

    @Override
    public ComponentFootprint footprint(@Nullable PlacedComponent placed) {
        if(placed != null && placed.get(MIRRORED)) {
            return mirroredFootprint.rotated(placed.get(ORIENTATION));
        }
        return super.footprint(placed);
    }

    @Override
    public boolean rotate(@NotNull PlacedComponent placed, boolean counterClockwise) {
        if(!counterClockwise) {
            if (!placed.get(MIRRORED)) {
                placed.set(MIRRORED, true);
                return true;
            }
            placed.set(MIRRORED, false);
        } else {
            if(placed.get(MIRRORED)) {
                placed.set(MIRRORED, false);
                return true;
            }
            placed.set(MIRRORED, true);
        }
        return super.rotate(placed, counterClockwise);
    }
}
