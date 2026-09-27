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
package io.github.sjzeero.eecircuits.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import io.github.sjzeero.eecircuits.EECircuits;

public class EEPartialModels {
    public static final PartialModel ELECTRON_TUBE_GLOW = model("component/triode_glow");
    public static final PartialModel PENTODE_GLOW = model("component/pentode_glow");
    public static final PartialModel THYRATRON_GLOW = model("component/thyratron_glow");
    public static final PartialModel REGULATOR_TUBE_GLOW = model("component/regulator_tube_glow");
    public static final PartialModel POTENTIOMETER_KNOB = model("component/potentiometer_knob");
    public static final PartialModel NEON_TUBE_BULB = model("component/neon_bulb_bulb");
    public static final PartialModel NEON_TUBE_GLOW = model("component/neon_bulb_glow");
    public static final PartialModel NEON_TUBE_VERTICAL_GLOW = model("component/neon_bulb_vertical_glow");
    public static final PartialModel LABEL = model("component/label");
    public static final PartialModel LIGHT_BULB_BULB = model("component/light_bulb_bulb");
    public static final PartialModel LIGHT_BULB_GLOW = model("component/light_bulb_glow");
    public static final PartialModel LIGHT_BULB_BULB_DYED = model("component/light_bulb_bulb_dyed");
    public static final PartialModel LIGHT_BULB_GLOW_DYED = model("component/light_bulb_glow_dyed");
    public static final PartialModel BARRETTER_GLOW = model("component/barretter_tube_glow");
    public static final PartialModel COMPONENT_GAUGE_NEEDLE = model("component/gauge_needle");
    public static final PartialModel CIRCUIT_TABLE_GLOW = model("block/circuit_design_table_glow");

    private static PartialModel model(String path) {
        return PartialModel.of(EECircuits.asResource(path));
    }

    public static void register() {
    }
}
