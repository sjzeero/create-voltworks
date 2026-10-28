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
 * Modified by sjzeero: ported from Create: Power Grid to Voltworks.
 */
package io.github.sjzeero.voltworks.battery;

public final class BatterySpec {
    private BatterySpec() {}

    public static final double CAPACITY_WH = 1000;
    public static final double CAPACITY = CAPACITY_WH * 3600;
    public static final double INITIAL_SOC = 0.9;
    public static final double FULL_VOLTAGE = 300;
    public static final double SCALE = FULL_VOLTAGE / 12.7;
    public static final double FULL_RESISTANCE = 0.1 * SCALE;
    public static final double MAX_RESISTANCE = 10000 * SCALE;

    public static double clampSoc(double soc) {
        return soc < 0 ? 0 : soc > 1 ? 1 : soc;
    }

    public static double voltage(double energy, double capacity) {
        if (energy <= 0 || capacity <= 0)
            return 0;
        return (1.2 * clampSoc(energy / capacity) + 11.5) * SCALE;
    }

    public static double resistance(double soc, int size) {
        double r = Math.min(MAX_RESISTANCE, Math.exp(-21.18323 * clampSoc(soc) + 10.58157) * SCALE + FULL_RESISTANCE);
        return r / Math.max(1, size);
    }

    public static int comparator(double energy, double capacity) {
        double fill = capacity <= 0 ? 0 : clampSoc(energy / capacity);
        return (int) Math.floor(fill * 14) + (fill > 0.001 ? 1 : 0);
    }

    public static double energyOut(double emf, double resistance, double terminal) {
        if (resistance <= 0)
            return 0;
        double current = (emf - terminal) / resistance;
        return emf > 0 ? emf * current : terminal * current;
    }
}
