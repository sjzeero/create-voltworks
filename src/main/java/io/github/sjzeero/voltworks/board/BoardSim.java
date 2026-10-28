package io.github.sjzeero.voltworks.board;

import io.github.sjzeero.voltworks.circuits.circuitboard.BakedCircuit;
import io.github.sjzeero.voltworks.sim.ElectricalNetwork;
import io.github.sjzeero.voltworks.sim.node.FloatingNode;
import io.github.sjzeero.voltworks.sim.node.VoltageSourceCoupling;
import io.github.sjzeero.voltworks.sim.solver.JavaMNA;
import io.github.sjzeero.voltworks.circuits.schematic.CircuitSchematic;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import io.github.sjzeero.voltworks.sim.node.INode;
import org.ejml.data.DMatrixRMaj;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

public class BoardSim {
    public static final double G_MIN = 1e-9;
    static final double MAX_G = 1e9;
    static final double MAX_J = 1e9;
    static final double PIN_RELAX = 0.5;

    public final int pins;
    public final int connectorPins;
    public final List<PlacedComponent> linkVias;
    public final BakedCircuit circuit;

    private final ElectricalNetwork network;
    private final FloatingNode[] pinNodes;
    private final VoltageSourceCoupling[] sources;

    final double[] pinU;
    final double[] g0;
    final double[][] gp;
    final double[] j0;
    final double[] current;
    private double[] lastU;
    private double[] lastDu;
    private int[] osc;
    private final double[][] y;
    private DMatrixRMaj rhs;
    private DMatrixRMaj sens;

    long serial = -1;
    private long steppedSerial = -1;
    private int steppedMicroTick = -1;
    boolean converged = true;
    int badSteps;

    public BoardSim(BakedCircuit circuit, double[] previousU) {
        this(circuit, previousU, List.of());
    }

    public BoardSim(BakedCircuit circuit, double[] previousU, List<PlacedComponent> linkVias) {
        this.circuit = circuit;
        this.linkVias = List.copyOf(linkVias);
        this.connectorPins = circuit.externalNodes.size();
        this.pins = connectorPins + this.linkVias.size();
        this.pinU = new double[pins];
        if (previousU != null)
            System.arraycopy(previousU, 0, pinU, 0, Math.min(pins, previousU.length));
        this.g0 = new double[pins];
        this.gp = new double[pins][pins];
        this.j0 = new double[pins];
        this.current = new double[pins];
        this.lastU = new double[pins];
        this.lastDu = new double[pins];
        this.osc = new int[pins];
        this.y = new double[pins][pins];
        for (int k = 0; k < pins; k++)
            g0[k] = G_MIN;

        network = new ElectricalNetwork(true);
        network.maxIterations = hooks -> 200;
        network.setPrecision(1e-7, 1e-14, 1e-6, 0.99);
        network.bjtSmoothAlpha = 0.5;
        network.diodeSmoothAlpha = 0.025;

        pinNodes = new FloatingNode[pins];
        for (int k = 0; k < connectorPins; k++)
            pinNodes[k] = circuit.externalNodes.get(k);
        var linkNodes = Collections.newSetFromMap(new IdentityHashMap<INode, Boolean>());
        for (int k = 0; k < this.linkVias.size(); k++) {
            var node = circuit.getNode(new CircuitSchematic.Node(this.linkVias.get(k), 0));
            pinNodes[connectorPins + k] = node;
            linkNodes.add(node);
        }
        sources = new VoltageSourceCoupling[pins];
        for (int k = 0; k < pins; k++) {
            network.addNode(pinNodes[k]);
            sources[k] = new VoltageSourceCoupling(pinNodes[k], null, 0f, (float) (k == 0 ? 0 : pinU[k]));
            network.addNode(sources[k]);
        }
        for (var node : circuit.internalNodes)
            if (!linkNodes.contains(node))
                network.addNode(node);
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
        long t0 = System.nanoTime();
        step(microTick, totalMicroTicks);
        BoardStats.SIM_NS.add(System.nanoTime() - t0);
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
            double j = current[k] - g * pinU[k] - pair;
            if (!Double.isFinite(g) || !Double.isFinite(j) || g > MAX_G || Math.abs(j) > MAX_J) {
                g0[k] = G_MIN;
                j0[k] = 0;
                badSteps++;
                continue;
            }
            double du = pinU[k] - lastU[k];
            if (du * lastDu[k] < 0 && Math.abs(du) > 1e-3)
                osc[k] = Math.min(osc[k] + 2, 10);
            else
                osc[k] = Math.max(osc[k] - 1, 0);
            lastU[k] = pinU[k];
            lastDu[k] = du;
            if (osc[k] >= 4) {
                g0[k] = G_MIN;
                j0[k] = 0;
                badSteps++;
                continue;
            }
            g0[k] = g;
            j0[k] = j;
        }
    }

    void rejectPin(int k) {
        pinU[k] = 0;
        g0[k] = G_MIN;
        j0[k] = 0;
        for (int j = 0; j < pins; j++)
            gp[k][j] = gp[j][k] = 0;
        badSteps++;
    }

    public double pinCurrent(int k) {
        return current[k];
    }

    public double[] pinU() {
        return pinU.clone();
    }
}
