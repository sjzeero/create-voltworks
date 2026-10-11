/*
 * Copyright 2025-2026 Create: Power Grid contributors (https://github.com/patryk3211/PowerGrid)
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
package io.github.sjzeero.voltworks.pgsim.special;

import io.github.sjzeero.voltworks.pgsim.SwitchedWire;
import io.github.sjzeero.voltworks.pgsim.node.IElectricNode;
import io.github.sjzeero.voltworks.pgsim.solver.IOuterHook;
import io.github.sjzeero.voltworks.Voltworks;

public class FuseSwitchWire extends SwitchedWire implements IOuterHook {
    private final float maxCurrent;

    private boolean wasDisconnected = false;
    private boolean blown = false;

    public FuseSwitchWire(float resistance, IElectricNode node1, IElectricNode node2, float maxCurrent) {
        super(resistance, node1, node2);
        this.maxCurrent = maxCurrent;
    }

    public FuseSwitchWire(float resistance, IElectricNode node1, IElectricNode node2, boolean initialState, float maxCurrent) {
        super(resistance, node1, node2, initialState);
        this.maxCurrent = maxCurrent;
    }

    @Override
    public void preSolve() {
        if(isConverged()) {
            var I = Math.abs(current());
            if (getState()) {
                if (I > maxCurrent && !wasDisconnected) {
                    setState(false);
                    blown = true;
                }
                wasDisconnected = false;
            } else {
                wasDisconnected = true;
            }
        }
    }

    public boolean wasBlown() {
        boolean was = blown;
        blown = false;
        return was;
    }
}
