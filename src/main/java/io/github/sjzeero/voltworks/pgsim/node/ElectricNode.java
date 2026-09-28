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
package io.github.sjzeero.voltworks.pgsim.node;

import io.github.sjzeero.voltworks.pgsim.ElectricalNetwork;

public abstract class ElectricNode implements IElectricNode {
    private int matrixIndex;

    protected double savedStateValue = 0;

    protected ElectricalNetwork network;

    @Override
    public void setStateValue(double value) {
        this.savedStateValue = value;
        IElectricNode.super.setStateValue(value);
    }

    @Override
    public void setNetwork(ElectricalNetwork network) {
        this.network = network;
    }

    @Override
    public double getSavedValue() {
        return savedStateValue;
    }

    @Override
    public void setSavedValue(double value) {
        this.savedStateValue = value;
    }

    @Override
    public ElectricalNetwork getNetwork() {
        return network;
    }

    public void assignIndex(int index) {
        matrixIndex = index;
    }

    public int getIndex() {
        return matrixIndex;
    }

    @Override
    public double getVoltage() {
        return 0;
    }

    @Override
    public double getCurrent() {
        return 0;
    }

    public void setVoltage(double voltage) {

    }

    public void setCurrent(double current) {

    }
}
