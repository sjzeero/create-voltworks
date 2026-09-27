package io.github.sjzeero.eecircuits.board;

import io.github.sjzeero.eecircuits.pgsim.AbstractElectricWire;
import io.github.sjzeero.eecircuits.pgsim.ElectricalNetwork;
import io.github.sjzeero.eecircuits.pgsim.node.FloatingNode;
import io.github.sjzeero.eecircuits.pgsim.node.INode;
import io.github.sjzeero.eecircuits.pgsim.node.VoltageSourceCoupling;
import io.github.sjzeero.eecircuits.pgsim.solver.JavaMNA;
import org.ejml.data.DMatrixRMaj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BoardSim {
    public static final double G_MIN = 1e-9;

    public final int pins;
    public final List<PlacedPart> parts;
    public final boolean[] used;
    public final int padCount;

    private final ElectricalNetwork network;
    private final FloatingNode[] padNodes;
    private final VoltageSourceCoupling[] sources;

    final double[] pinU;
    final double[] g0;
    final double[][] gp;
    final double[] j0;
    private final double[] current;
    private final double[][] y;
    private DMatrixRMaj rhs;
    private DMatrixRMaj sens;

    long serial = -1;
    private long steppedSerial = -1;
    private int steppedMicroTick = -1;
    int microTicks = 1;
    double[][] wavePinCurrent = new double[1][];
    double[][] wavePadVoltage = new double[1][];
    double[][] wavePartCurrent = new double[1][];
    boolean converged = true;
    int badSteps;

    public BoardSim(int pins, List<PlacedPart> parts, double[] previousU) {
        this.pins = pins;
        this.parts = parts;
        this.used = new boolean[pins];
        this.pinU = new double[pins];
        if (previousU != null)
            System.arraycopy(previousU, 0, pinU, 0, Math.min(pins, previousU.length));
        this.g0 = new double[pins];
        this.gp = new double[pins][pins];
        this.j0 = new double[pins];
        this.current = new double[pins];
        this.y = new double[pins][pins];
        for (int k = 0; k < pins; k++)
            g0[k] = G_MIN;

        int maxPad = pins - 1;
        for (PlacedPart part : parts)
            for (int pad : part.pads)
                maxPad = Math.max(maxPad, pad);
        padCount = maxPad + 1;

        network = new ElectricalNetwork(true);
        network.maxIterations = hooks -> 200;
        network.setPrecision(1e-7, 1e-14, 1e-6, 0.99);
        network.bjtSmoothAlpha = 0.5;
        network.diodeSmoothAlpha = 0.025;

        padNodes = new FloatingNode[padCount];
        sources = new VoltageSourceCoupling[pins];
        for (int k = 0; k < pins; k++) {
            padNodes[k] = new FloatingNode();
            network.addNode(padNodes[k]);
            sources[k] = new VoltageSourceCoupling(padNodes[k], null, 0f, (float) (k == 0 ? 0 : pinU[k]));
            network.addNode(sources[k]);
        }

        List<INode> internalNodes = new ArrayList<>();
        List<AbstractElectricWire> wires = new ArrayList<>();
        BoardCircuitBuilder builder = new BoardCircuitBuilder(this::padNode, internalNodes, wires);
        for (PlacedPart part : parts) {
            part.wires.clear();
            part.component.bake(part, builder);
            for (int pad : part.pads)
                if (pad < pins)
                    used[pad] = true;
        }
        used[0] = true;
        internalNodes.forEach(network::addNode);
        wires.forEach(network::addWire);
    }

    private FloatingNode padNode(int pad) {
        if (padNodes[pad] == null) {
            padNodes[pad] = new FloatingNode();
            network.addNode(padNodes[pad]);
        }
        return padNodes[pad];
    }

    void ensureStep(long serial, int microTick, int totalMicroTicks) {
        if (steppedSerial == serial && steppedMicroTick == microTick)
            return;
        steppedSerial = serial;
        steppedMicroTick = microTick;
        step(microTick, totalMicroTicks);
    }

    private void step(int microTick, int totalMicroTicks) {
        if (microTick == 0) {
            if (microTicks != totalMicroTicks || wavePinCurrent.length != totalMicroTicks) {
                microTicks = totalMicroTicks;
                wavePinCurrent = new double[totalMicroTicks][];
                wavePadVoltage = new double[totalMicroTicks][];
                wavePartCurrent = new double[totalMicroTicks][];
            }
            network.prepare(totalMicroTicks);
        }
        for (int k = 1; k < pins; k++)
            sources[k].setVoltage(pinU[k]);
        network.singleTick();
        converged = network.isConverged();

        for (int k = 0; k < pins; k++)
            current[k] = -sources[k].getCurrent();
        if (linearize())
            updateEquivalent();
        else
            badSteps++;
        record(microTick);
    }

    private boolean linearize() {
        if (!(network.getMna() instanceof JavaMNA mna))
            return false;
        int n = network.getNodes().size();
        if (rhs == null || rhs.getNumRows() != n) {
            rhs = new DMatrixRMaj(n, 1);
            sens = new DMatrixRMaj(n, 1);
        }
        for (int j = 1; j < pins; j++) {
            if (!used[j])
                continue;
            rhs.zero();
            rhs.set(sources[j].getIndex(), 0, 1);
            if (!mna.solveLinearized(rhs, sens))
                return false;
            for (int k = 1; k < pins; k++)
                y[k][j] = -sens.get(sources[k].getIndex(), 0);
        }
        for (int k = 0; k < pins; k++)
            if (!Double.isFinite(current[k]))
                return false;
        return true;
    }

    private void updateEquivalent() {
        for (int k = 1; k < pins; k++) {
            for (int j = k + 1; j < pins; j++) {
                double g = 0;
                if (used[k] && used[j]) {
                    double a = -y[k][j];
                    double b = -y[j][k];
                    if (a > 0 && b > 0)
                        g = Math.min(a, b);
                }
                gp[k][j] = gp[j][k] = g;
            }
        }
        for (int k = 1; k < pins; k++) {
            if (!used[k])
                continue;
            double g = y[k][k];
            double pair = 0;
            for (int j = 1; j < pins; j++) {
                if (j == k)
                    continue;
                g -= gp[k][j];
                pair += gp[k][j] * (pinU[k] - pinU[j]);
            }
            g = Math.max(G_MIN, g);
            g0[k] = g;
            j0[k] = current[k] - g * pinU[k] - pair;
        }
    }

    private void record(int microTick) {
        double[] pinI = new double[pins];
        System.arraycopy(current, 0, pinI, 0, pins);
        double[] padV = new double[padCount];
        for (int p = 0; p < padCount; p++)
            padV[p] = padNodes[p] == null ? 0 : padNodes[p].getVoltage();
        double[] partI = new double[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            PlacedPart part = parts.get(i);
            partI[i] = part.wires.isEmpty() ? 0 : part.wires.get(0).current();
        }
        if (microTick < wavePinCurrent.length) {
            wavePinCurrent[microTick] = pinI;
            wavePadVoltage[microTick] = padV;
            wavePartCurrent[microTick] = partI;
        }
    }

    public double[] pinU() {
        return pinU.clone();
    }
}
