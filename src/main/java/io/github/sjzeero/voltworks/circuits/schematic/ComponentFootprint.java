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
 * Modified by sjzeero: ported from Create: Power Grid to CEE: Voltworks.
 */
package io.github.sjzeero.voltworks.circuits.schematic;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.components.ComponentRegistry;
import io.github.sjzeero.voltworks.circuits.components.properties.Orientation;

import java.util.*;

import static io.github.sjzeero.voltworks.circuits.schematic.CircuitLayer.GRID_TO_GRID_SCALE;

public class ComponentFootprint {

    private static final PadData NONE = new PadData(-1, null, null);

    private final int width;
    private final int height;

    public final int originalWidth, originalHeight;

    private final SortedMap<Point, PadData> pads;
    private final boolean outline;
    private final boolean withItem;
    @Nullable
    private final Orientation arrow;

    private ItemStack renderedStack;

    protected ComponentFootprint(int width, int height, int originalWidth, int originalHeight, SortedMap<Point, PadData> pads, boolean outline, boolean withItem, @Nullable Orientation arrow) {
        this.width = width;
        this.height = height;
        this.originalWidth = originalWidth;
        this.originalHeight = originalHeight;
        this.pads = pads;
        this.outline = outline;
        this.withItem = withItem;
        this.arrow = arrow;
    }

    @Nullable
    public Component getTooltip(int mouseX, int mouseY) {
        var pad = pads.get(new Point(mouseX, mouseY));
        if(pad == null)
            return null;
        return pad.tooltip;
    }

    @Nullable
    public ItemStack getRenderedStack(io.github.sjzeero.voltworks.circuits.components.Component component) {
        if(!withItem)
            return null;
        if(renderedStack == null)
            renderedStack = ComponentRegistry.getItem(component).getDefaultInstance();
        return renderedStack;
    }

    public boolean hasOutline() {
        return outline;
    }

    public boolean hasItem() {
        return withItem;
    }

    @Nullable
    public Orientation getArrow() {
        return arrow;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getOriginalWidth() {
        return originalWidth;
    }

    public int getOriginalHeight() {
        return originalHeight;
    }

    public Map<Point, PadData> getPads() {
        return pads;
    }

    public ComponentFootprint rotated(Orientation orientation) {
        int width, height;
        if(orientation == Orientation.UP || orientation == Orientation.DOWN) {
            width = this.height;
            height = this.width;
        } else {
            width = this.width;
            height = this.height;
        }
        var pads = new TreeMap<Point, PadData>();
        for(var pad : this.pads.entrySet()) {
            var position = pad.getKey();
            int x, y;
            switch(orientation) {
                case RIGHT -> {
                    // No rotation
                    x = position.x();
                    y = position.y();
                }
                case DOWN -> {
                    // 90 degree rotation
                    x = this.height - position.y() - 1;
                    y = position.x();
                }
                case LEFT -> {
                    // 180 degree rotation
                    x = this.width - position.x() - 1;
                    y = this.height - position.y() - 1;
                }
                case UP -> {
                    // 270 degree rotation
                    x = position.y();
                    y = this.width - position.x() - 1;
                }
                default -> throw new IllegalStateException("Invalid orientation: " + orientation);
            }
            pads.put(new Point(x, y), pad.getValue());
        }

        var footprint = new ComponentFootprint(width, height, originalWidth, originalHeight, pads, this.outline, withItem, arrow == null ? null : arrow.rotate(orientation));
        // Copy cached stack if one is available.
        footprint.renderedStack = this.renderedStack;
        return footprint;
    }

    public ComponentFootprint mirroredX() {
        var pads = new TreeMap<Point, PadData>();
        for(var pad : this.pads.entrySet()) {
            var position = pad.getKey();
            int x, y;
            x = this.width - position.x() - 1;
            y = position.y();
            pads.put(new Point(x, y), pad.getValue());
        }

        var footprint = new ComponentFootprint(width, height, originalWidth, originalHeight, pads, this.outline, withItem, arrow == null ? null : arrow.isX() ? arrow.getOpposite() : arrow);
        // Copy cached stack if one is available.
        footprint.renderedStack = this.renderedStack;
        return footprint;
    }

    public ComponentFootprint mirroredY() {
        var pads = new TreeMap<Point, PadData>();
        for(var pad : this.pads.entrySet()) {
            var position = pad.getKey();
            int x, y;
            x = position.x();
            y = this.height - position.y() - 1;
            pads.put(new Point(x, y), pad.getValue());
        }

        var footprint = new ComponentFootprint(width, height, originalWidth, originalHeight, pads, this.outline, withItem, arrow == null ? null : arrow.isY() ? arrow.getOpposite() : arrow);
        // Copy cached stack if one is available.
        footprint.renderedStack = this.renderedStack;
        return footprint;
    }

    public static class Builder {
        private final int width, height;
        private final SortedMap<Point, PadData> pads = new TreeMap<>();
        private boolean withItem;
        private boolean outline = false;
        private Orientation arrow = null;
        @Nullable
        private final String translationKey;
        @Nullable
        private final String sharedKeyBase;
        public final Map<String, String> translatedPads = new HashMap<>();

        public Builder(int width, int height) {
            this(width, height, null, null);
        }

        public Builder(int width, int height, @Nullable String translationKeyBase, @Nullable String sharedKeyBase) {
            this.width = width;
            this.height = height;
            this.translationKey = translationKeyBase;
            this.sharedKeyBase = sharedKeyBase;
        }

        private void validatePad(int x, int y) {
            if(x < 0 || y < 0 || x >= width * GRID_TO_GRID_SCALE || y >= height * GRID_TO_GRID_SCALE)
                throw new IllegalArgumentException("Pad position must be inside defined footprint size");
        }

        public Builder addPad(int x, int y) {
            validatePad(x, y);
            pads.put(new Point(x, y), NONE);
            return this;
        }

        public Builder addPad(int x, int y, int nodeIndex) {
            return addPad(x, y, nodeIndex, (Component) null, null);
        }

        public Builder addPad(int x, int y, int nodeIndex, @Nullable Component tooltip, @Nullable Component shortText) {
            validatePad(x, y);
            pads.put(new Point(x, y), new PadData(nodeIndex, tooltip, shortText));
            return this;
        }

        public Builder addPad(int x, int y, int nodeIndex, String defaultLang, @Nullable String defaultShort) {
            if(translationKey == null)
                throw new IllegalCallerException("This method may only be used when the translation key base is set");
            var key = translationKey + "." + nodeIndex;
            translatedPads.put(key, defaultLang);
            if(defaultShort != null)
                translatedPads.put(key + ".short", defaultShort);
            return addPad(x, y, nodeIndex, Component.translatable(key), defaultShort == null ? null : Component.translatable(key + ".short"));
        }

        public Builder addPadSharedText(int x, int y, int nodeIndex, @NotNull String key, @Nullable String keyShort) {
            if(sharedKeyBase == null)
                throw new IllegalCallerException("This method may only be used when the translation key base is set");
            return addPad(x, y, nodeIndex,
                    Component.translatable(sharedKeyBase + "." + key),
                    keyShort == null ? null : Component.translatable(sharedKeyBase + "." + keyShort));
        }

        public Builder addPadSharedText(int x, int y, int nodeIndex, @NotNull String key) {
            return addPadSharedText(x, y, nodeIndex, key, key + ".short");
        }

        public Builder withOutline() {
            outline = true;
            return this;
        }

        public Builder withItem() {
            this.withItem = true;
            return this;
        }

        public Builder withArrow(Orientation facing) {
            this.arrow = facing;
            return this;
        }

        public Builder withArrow() {
            this.arrow = Orientation.RIGHT;
            return this;
        }

        public ComponentFootprint build() {
            var padIndices = new TreeSet<Integer>();
            for(var pad : pads.values()) {
                if(pad.nodeIndex >= 0)
                    padIndices.add(pad.nodeIndex);
            }
            if(!padIndices.isEmpty()) {
                if (padIndices.first() != 0)
                    throw new IllegalStateException("Footprint pad indices must start from 0");
            }
            return new ComponentFootprint(width, height, width, height, pads, outline, withItem, arrow);
        }
    }

    public record PadData(int nodeIndex, @Nullable Component tooltip, @Nullable Component shortText) { }
}
