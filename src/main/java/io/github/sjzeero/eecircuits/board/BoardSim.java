package io.github.sjzeero.eecircuits.board;

import io.github.sjzeero.eecircuits.circuits.circuitboard.BakedCircuit;
import io.github.sjzeero.eecircuits.pgsim.ElectricalNetwork;
import io.github.sjzeero.eecircuits.pgsim.node.FloatingNode;
import io.github.sjzeero.eecircuits.pgsim.node.VoltageSourceCoupling;
import io.github.sjzeero.eecircuits.pgsim.solver.JavaMNA;
import org.ejml.data.DMatrixRMaj;

public class BoardSim {
    public static final double G_MIN = 1e-9;

    public final int pins;
    public final BakedCircuit circuit;

    private final ElectricalNetwork network;
    private final FloatingNode[] pinNodes;
    private final VoltageSourceCoupling[] sources;

    final double[] pinU;
    final double[] g0;
    final double[][] gp;
    final double[] j0;
    final double[] current;
    private final double[][] y;
    private DMatrixRMaj rhs;
    private DMatrixRMaj sens;

    long serial = -1;
    private long steppedSerial = -1;
    private int steppedMicroTick = -1;
    boolean converged = true;
    int badSteps;

    public BoardSim(BakedCircuit circuit, double[] previousU) {
        this.circuit = circuit;
        this.pins = circuit.externalNodes.size();
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

        network = new ElectricalNetwork(true);
        network.maxIterations = hooks -> 200;
        network.setPrecision(1e-7, 1e-14, 1e-6, 0.99);
        network.bjtSmoothAlpha = 0.5;
        network.diodeSmoothAlpha = 0.025;

        pinNodes = circuit.externalNodes.toArray(new FloatingNode[0]);
        sources = new VoltageSourceCoupling[pins];
        for (int k = 0; k < pins; k++) {
            network.addNode(pinNodes[k]);
            sources[k] = new VoltageSourceCoupling(pinNodes[k], null, 0f, (float) (k == 0 ? 0 : pinU[k]));
            network.addNode(sources[k]);
        }
        circuit.internalNodes.forEach(network::addNode);
        circuit.wires.forEach(network::addWire);
    }

    public boolean coupled() {
        return pins >= 2;
    }

    void ensureStep(long serial, int microTick, int totalMicroTicks) {
        if (steppedSerial == serial && steppedMicroTick == microTick)
            return;
        steppedSerial = serial;
        steppedMicroTick = microTick;
        step(microTick, totalMicroTicks);
    }

    public void standaloneTick(int totalMicroTicks) {
        for (int m = 0; m < totalMicroTicks; m++)
            step(m, totalMicroTicks);
    }

    private void step(int microTick, int totalMicroTicks) {
        if (microTick == 0)
            network.prepare(totalMicroTicks);
        for (int k = 1; k < pins; k++)
            sources[k].setVoltage(pinU[k]);
        network.singleTick();
        converged = network.isConverged();
        if (!coupled())
            return;
        for (int k = 0; k < pins; k++)
            current[k] = -sources[k].getCurrent();
        if (linearize())
            updateEquivalent();
        else
            badSteps++;
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
                double a = -y[k][j];
                double b = -y[j][k];
                if (a > 0 && b > 0)
                    g = Math.min(a, b);
                gp[k][j] = gp[j][k] = g;
            }
        }
        for (int k = 1; k < pins; k++) {
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

    public double pinCurrent(int k) {
        return current[k];
    }

    public double[] pinU() {
        return pinU.clone();
    }
}
