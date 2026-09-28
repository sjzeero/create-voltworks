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
 * Modified by sjzeero: ported from Create: Power Grid to Create: Voltworks.
 */
package io.github.sjzeero.eecircuits.circuits;

import io.github.sjzeero.eecircuits.EECircuitsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class Thermal {
    public static final float STANDARD_TEMPERATURE = 22.0f;
    public static final float ABSOLUTE_ZERO = -273.15f;
    public static final int OVERHEAT_TICKS = 2;

    private Thermal() {
    }

    public static float getAmbientTemperature(Level level, BlockPos pos) {
        return 13.65f * level.getBiome(pos).value().getBaseTemperature() + 7.1f;
    }

    public static float dissipationFactor(float power, float temperature) {
        return power / (temperature - STANDARD_TEMPERATURE);
    }

    public static boolean shouldOverheat() {
        return EECircuitsConfig.overheating();
    }
}
