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
package io.github.sjzeero.eecircuits.board;

import io.github.sjzeero.eecircuits.pgsim.AbstractElectricWire;
import io.github.sjzeero.eecircuits.pgsim.ElectricWire;
import io.github.sjzeero.eecircuits.pgsim.node.FloatingNode;
import io.github.sjzeero.eecircuits.pgsim.node.IElectricNode;
import io.github.sjzeero.eecircuits.pgsim.node.INode;

import java.util.Collection;
import java.util.function.IntFunction;

public class BoardCircuitBuilder {
    private final IntFunction<FloatingNode> padNodes;
    private final Collection<INode> internalNodes;
    private final Collection<AbstractElectricWire> wires;

    public BoardCircuitBuilder(IntFunction<FloatingNode> padNodes, Collection<INode> internalNodes, Collection<AbstractElectricWire> wires) {
        this.padNodes = padNodes;
        this.internalNodes = internalNodes;
        this.wires = wires;
    }

    public FloatingNode terminalNode(int index) {
        return padNodes.apply(index);
    }

    public FloatingNode addInternalNode() {
        var node = new FloatingNode();
        add(node);
        return node;
    }

    public void add(INode node) {
        internalNodes.add(node);
    }

    public void add(AbstractElectricWire wire) {
        wires.add(wire);
    }

    public ElectricWire connect(float resistance, IElectricNode node1, IElectricNode node2) {
        var wire = new ElectricWire(resistance, node1, node2);
        add(wire);
        return wire;
    }
}
