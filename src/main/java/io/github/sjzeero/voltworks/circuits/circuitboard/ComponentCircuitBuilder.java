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
 * Modified by sjzeero: ported from Create: Power Grid to Create: Voltworks.
 */
package io.github.sjzeero.voltworks.circuits.circuitboard;

import io.github.sjzeero.voltworks.pgsim.AbstractElectricWire;
import io.github.sjzeero.voltworks.pgsim.ElectricWire;
import io.github.sjzeero.voltworks.pgsim.SwitchedWire;
import io.github.sjzeero.voltworks.pgsim.node.FloatingNode;
import io.github.sjzeero.voltworks.pgsim.node.IElectricNode;
import io.github.sjzeero.voltworks.pgsim.node.INode;

import java.util.Collection;
import java.util.function.Function;

public class ComponentCircuitBuilder {
    private final Function<Integer, FloatingNode> externalNodeProvider;
    private final Collection<INode> internalNodes;
    private final Collection<AbstractElectricWire> wires;

    public ComponentCircuitBuilder(Function<Integer, FloatingNode> externalNodeProvider, Collection<INode> internalNodes, Collection<AbstractElectricWire> wires) {
        this.externalNodeProvider = externalNodeProvider;
        this.internalNodes = internalNodes;
        this.wires = wires;
    }

    public FloatingNode terminalNode(int index) {
        return externalNodeProvider.apply(index);
    }

    public FloatingNode addInternalNode() {
        var node = new FloatingNode();
        add(node);
        return node;
    }

    public ElectricWire connect(float resistance, IElectricNode node1, IElectricNode node2) {
        var wire = new ElectricWire(resistance, node1, node2);
        add(wire);
        return wire;
    }

    public SwitchedWire connectSwitch(float resistance, IElectricNode node1, IElectricNode node2, boolean state) {
        var wire = new SwitchedWire(resistance, node1, node2, state);
        add(wire);
        return wire;
    }

    public SwitchedWire connectSwitch(float resistance, IElectricNode node1, IElectricNode node2) {
        return connectSwitch(resistance, node1, node2, true);
    }

    public void add(AbstractElectricWire wire) {
        wires.add(wire);
    }

    public void add(INode node) {
        internalNodes.add(node);
    }
}
