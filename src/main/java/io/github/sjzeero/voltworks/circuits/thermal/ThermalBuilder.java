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
package io.github.sjzeero.voltworks.circuits.thermal;

import io.github.sjzeero.voltworks.circuits.Thermal;
import io.github.sjzeero.voltworks.sim.AbstractElectricWire;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import io.github.sjzeero.voltworks.Voltworks;

public class ThermalBuilder {
    private final Set<AbstractElectricWire> sources = new HashSet<>();
    private float overheatTemperature;
    private float thermalMass;
    private float dissipationFactor;
    private Consumer<Float> temperatureCallback;
    private Runnable overheatCallback;

    private final UUID componentUUID;
    private final int index;

    public ThermalBuilder(UUID componentUUID, int index) {
        this.componentUUID = componentUUID;
        this.index = index;

        overheatTemperature = 175f;
        thermalMass = 0;
        dissipationFactor = 0;
        temperatureCallback = null;
    }

    public ThermalBuilder addHeatSource(AbstractElectricWire wire) {
        sources.add(wire);
        return this;
    }

    public ThermalBuilder setThermalMass(float thermalMass) {
        this.thermalMass = thermalMass;
        return this;
    }

    public ThermalBuilder setMaxPower(float power, float temperature) {
        this.dissipationFactor = Thermal.dissipationFactor(power, temperature);
        return this;
    }

    public ThermalBuilder setMaxCurrent(float current, float resistance, float temperature) {
        return setMaxPower(resistance * current * current, temperature);
    }

    public ThermalBuilder setDissipationFactor(float dissipationFactor) {
        this.dissipationFactor = dissipationFactor;
        return this;
    }

    public ThermalBuilder setOverheatTemperature(float overheatTemperature) {
        this.overheatTemperature = overheatTemperature;
        return this;
    }

    public ThermalBuilder withTemperatureCallback(Consumer<Float> temperatureCallback) {
        this.temperatureCallback = temperatureCallback;
        return this;
    }

    public ThermalBuilder withOverheatCallback(Runnable overheatCallback) {
        this.overheatCallback = overheatCallback;
        return this;
    }

    public ThermalUnit build() {
        if(thermalMass == 0)
            throw new IllegalStateException("Thermal mass cannot be zero");
        return new ThermalUnit(componentUUID, index, thermalMass, dissipationFactor, overheatTemperature, sources, temperatureCallback, overheatCallback);
    }

    public interface IEmitter {
        ThermalBuilder builder();
    }
}
