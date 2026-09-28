/*
 * Copyright 2025-2026 patryk3211
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
package io.github.sjzeero.voltworks.circuits.components;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class TerminalBoundingBox {
    public static final Component CONNECTOR = Component.translatable("voltworks.terminal.connector");
    public static final int GRAY = 0x808080;
    public static final int RED = 0xE03030;

    private final Component name;
    private final Vec3 min;
    private final Vec3 max;
    private final Vec3 origin;
    private int color = GRAY;

    public TerminalBoundingBox(Component name, double x1, double y1, double z1, double x2, double y2, double z2) {
        this(name, new Vec3(x1 / 16.0, y1 / 16.0, z1 / 16.0), new Vec3(x2 / 16.0, y2 / 16.0, z2 / 16.0));
    }

    private TerminalBoundingBox(Component name, Vec3 min, Vec3 max) {
        this.name = name;
        this.min = min;
        this.max = max;
        this.origin = min.add(max).scale(0.5);
    }

    public TerminalBoundingBox withColor(int color) {
        var box = new TerminalBoundingBox(name, min, max);
        box.color = color;
        return box;
    }

    public TerminalBoundingBox offset(double x, double y, double z) {
        var box = new TerminalBoundingBox(name, min.add(x, y, z), max.add(x, y, z));
        box.color = color;
        return box;
    }

    public Component getName() {
        return name;
    }

    public Vec3 getOrigin() {
        return origin;
    }

    public double getSize() {
        return Math.max(max.x - min.x, Math.max(max.y - min.y, max.z - min.z));
    }

    public int getColor() {
        return color;
    }
}
