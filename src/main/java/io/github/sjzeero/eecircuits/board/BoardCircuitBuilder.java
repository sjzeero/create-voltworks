package io.github.sjzeero.eecircuits.board;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import net.minecraft.core.BlockPos;

import java.util.function.IntUnaryOperator;

public class BoardCircuitBuilder {
    public static final int INTERNAL_BASE = 64;

    private final BridgeCollector bridges;
    private final BlockPos pos;
    private final IntUnaryOperator terminals;
    private int nextInternal;

    public BoardCircuitBuilder(BridgeCollector bridges, BlockPos pos, IntUnaryOperator terminals, int firstInternal) {
        this.bridges = bridges;
        this.pos = pos;
        this.terminals = terminals;
        this.nextInternal = firstInternal;
    }

    public InWorldNode terminalNode(int index) {
        return new InWorldNode(terminals.applyAsInt(index), pos);
    }

    public InWorldNode addInternalNode() {
        InWorldNode node = new InWorldNode(nextInternal++, pos);
        bridges.addInternalNode(node);
        return node;
    }

    public ElectricalProperties connect(double resistance, InWorldNode node1, InWorldNode node2) {
        ElectricalProperties properties = ElectricalProperties.resistor(Math.max(resistance, 0.001));
        bridges.bridge(node1, node2, properties);
        return properties;
    }

    public int nextInternalId() {
        return nextInternal;
    }
}
