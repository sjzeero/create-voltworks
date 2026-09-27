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
package io.github.sjzeero.eecircuits.pgsim.special;

import io.github.sjzeero.eecircuits.pgsim.ElectricWire;
import io.github.sjzeero.eecircuits.pgsim.SwitchedWire;
import io.github.sjzeero.eecircuits.pgsim.node.IElectricNode;
import io.github.sjzeero.eecircuits.pgsim.solver.IOuterHook;

public class RelaySwitchWire extends SwitchedWire implements IOuterHook {
    private final ElectricWire coilWire;
    private final float onCurrent;
    private final float offCurrent;
    private final boolean normallyClosed;
    private final boolean polarized;

    private boolean switched = false;

    public RelaySwitchWire(float resistance, IElectricNode node1, IElectricNode node2, ElectricWire coilWire, float onCurrent, float offCurrent, boolean normallyClosed) {
        super(resistance, node1, node2);
        this.coilWire = coilWire;
        this.onCurrent = onCurrent;
        this.offCurrent = offCurrent;
        this.normallyClosed = normallyClosed;
        this.polarized = false;
    }

    public RelaySwitchWire(float resistance, IElectricNode node1, IElectricNode node2, boolean initialState, ElectricWire coilWire, float onCurrent, float offCurrent, boolean normallyClosed, boolean polarized) {
        super(resistance, node1, node2, initialState);
        this.coilWire = coilWire;
        this.onCurrent = onCurrent;
        this.offCurrent = offCurrent;
        this.normallyClosed = normallyClosed;
        this.polarized = polarized;
    }

    @Override
    public void preSolve() {
        if(coilWire.isConverged()) {
            if (polarized) {
                var I = coilWire.current();
                if ((getState() == normallyClosed) && I > onCurrent) { // Forward
                    setState(!normallyClosed);
                    switched = true;
                } else if ((getState() != normallyClosed) && I < -onCurrent) { // Reverse
                    setState(normallyClosed);
                    switched = true;
                }
            } else {
                var I = Math.abs(coilWire.current());
                if ((getState() != normallyClosed) && I < offCurrent) {
                    setState(normallyClosed);
                    switched = true;
                } else if ((getState() == normallyClosed) && I > onCurrent) {
                    setState(!normallyClosed);
                    switched = true;
                }
            }
        }
    }

    public boolean wasSwitched() {
        boolean was = switched;
        switched = false;
        return was;
    }
}
