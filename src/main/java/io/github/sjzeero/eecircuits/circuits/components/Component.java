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
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import io.github.sjzeero.eecircuits.EECircuits;
import io.github.sjzeero.eecircuits.circuits.circuitboard.ComponentCircuitBuilder;
import io.github.sjzeero.eecircuits.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.eecircuits.circuits.components.properties.ConstantProperty;
import io.github.sjzeero.eecircuits.circuits.components.properties.Orientation;
import io.github.sjzeero.eecircuits.circuits.components.properties.StringProperty;
import io.github.sjzeero.eecircuits.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.eecircuits.circuits.schematic.PlacedComponent;
import io.github.sjzeero.eecircuits.circuits.thermal.ThermalBuilder;
import io.github.sjzeero.eecircuits.circuits.Unit;

import java.util.Collection;
import java.util.List;

public abstract class Component {
    public static final StringProperty LABEL = new StringProperty(EECircuits.MOD_ID, "label");

    public static final float BASE_Y = 2 / 16f;

    private final ComponentFootprint footprint;
    private final ImmutableList<ComponentProperty<?>> properties;

    public Component(ComponentFootprint footprint) {
        this.footprint = footprint;

        var properties = new ImmutableList.Builder<ComponentProperty<?>>();
        addProperties(properties);
        this.properties = properties.build();
    }

    static ConstantProperty c(String name, net.minecraft.network.chat.Component value) {
        return new ConstantProperty(EECircuits.MOD_ID, name, value);
    }

    public static ConstantProperty power(float value) {
        return new ConstantProperty(EECircuits.MOD_ID, "power", Unit.POWER.formatWithPrefixes(value).component());
    }

    public static ConstantProperty current(float resistance, float power) {
        return current((float) Math.sqrt(power / resistance));
    }

    public static ConstantProperty current(float value) {
        return new ConstantProperty(EECircuits.MOD_ID, "current", Unit.CURRENT.formatWithPrefixes(value).component());
    }

    public static ConstantProperty voltage(float value) {
        return new ConstantProperty(EECircuits.MOD_ID, "voltage", Unit.VOLTAGE.formatWithPrefixes(value).component());
    }

    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {

    }

    public float getPadResistance(int padIndex) {
        return 0.002f;
    }

    public boolean emitExternalTerminals() {
        return false;
    }

    public boolean isExternalNode(int nodeIndex) {
        return emitExternalTerminals();
    }

    public List<TerminalBoundingBox> terminals(@NotNull PlacedComponent placed) {
        return List.of();
    }

    public abstract void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, @NotNull ThermalBuilder.IEmitter thermals);

    /**
     * Called every tick as long as the returned value is true
     * @param placed Placed component state
     * @return Continue ticking
     */
    public boolean tick(@NotNull PlacedComponent placed) {
        return false;
    }

    /**
     * Called when state is updated externally
     * @param placed Placed component state
     */
    public void stateUpdated(@NotNull PlacedComponent placed) {

    }

    public boolean rotate(@NotNull PlacedComponent placed, boolean counterClockwise) {
        return false;
    }

    public ComponentFootprint footprint(@Nullable PlacedComponent placed) {
        return footprint;
    }

    public ImmutableList<ComponentProperty<?>> getProperties() {
        return properties;
    }

    public boolean canPlace(@NotNull PlacedComponent placed, int x, int y) {
        return true;
    }

    @NotNull
    public ResourceLocation getModelId(@NotNull PlacedComponent component) {
        return ComponentRegistry.getId(this);
    }

    @NotNull
    public Collection<ResourceLocation> requestedModels() {
        return List.of(ComponentRegistry.getId(this));
    }

    @Nullable
    public static Orientation getEdge(@NotNull PlacedComponent placed) {
        if(placed.x == 0)
            return Orientation.LEFT;
        if(placed.y == 0)
            return Orientation.UP;
        if(placed.x == 16 - placed.footprint().getWidth())
            return Orientation.RIGHT;
        if(placed.y == 16 - placed.footprint().getHeight())
            return Orientation.DOWN;
        return null;
    }

    public static void renderDataTick(@NotNull PlacedComponent placed) {
        placed.data(FloatPair.class).ifPresent(data -> data.prev = data.current);
    }

    /**
     * Data fixer, handles changes between updates.
     * @param tag Tag to modify
     */
    public void dataFixup(@NotNull CompoundTag tag, int version) {

    }

    public static class FloatPair {
        float prev, current;

        public float value() {
            return current;
        }

        public float lerped(float partial) {
            return Mth.lerp(partial, prev, current);
        }
    }
}
