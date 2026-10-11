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
package io.github.sjzeero.voltworks.sim.special;

import net.minecraft.util.Mth;
import io.github.sjzeero.voltworks.sim.AbstractElectricWire;
import io.github.sjzeero.voltworks.sim.node.IElectricNode;
import io.github.sjzeero.voltworks.sim.solver.IOuterHook;

import static io.github.sjzeero.voltworks.sim.ElectricalNetwork.G_MIN;
import io.github.sjzeero.voltworks.Voltworks;

public class BarretterWire extends AbstractElectricWire implements IOuterHook {
    private final float holdingCurrent;
    private final float maximumConductance;

    private double currentConductance;

    public BarretterWire(float holdingCurrent, float minimumResistance, IElectricNode node1, IElectricNode node2) {
        super(node1, node2);
        this.holdingCurrent = holdingCurrent;
        this.maximumConductance = 1 / minimumResistance;
        currentConductance = maximumConductance;
    }

    @Override
    public void preSolve() {
        double G_target = maximumConductance;
        if(potentialDifference() != 0) {
            G_target = holdingCurrent / Math.abs(potentialDifference());
        }
        var G = Mth.clamp(G_target, G_MIN, maximumConductance);
        network.updateConductance(this, G - currentConductance);
        currentConductance = G;
    }

    @Override
    public double conductance() {
        return currentConductance;
    }
}
