package io.github.sjzeero.voltworks.board;

import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;
import io.github.sjzeero.voltworks.VoltageGuard;

public class PinBranch extends MicroTickingElectricalProperties {
    private final BoardSim sim;
    private final int k;
    private final int j;
    private final long serial;

    public PinBranch(BoardSim sim, int k, int j, long serial) {
        this.sim = sim;
        this.k = k;
        this.j = j;
        this.serial = serial;
        this.resistance = 1 / BoardSim.G_MIN;
    }

    @Override
    public void tick(double[] allVoltages, int microTick, int totalMicroTicks, int n1, int n2) {
        sim.ensureStep(serial, microTick, totalMicroTicks);
        if (j == 0) {
            resistance = 1 / sim.g0[k];
            currentSource = sim.j0[k];
        } else {
            double g = sim.gp[k][j];
            resistance = g > 1e-11 ? 1 / g : 1e11;
            currentSource = 0;
        }
    }

    @Override
    public void afterTick(double[] allVoltages, int n1, int n2, int microTick, int totalMicroTicks) {
        if (j == 0) {
            double u = allVoltages[n1 * totalMicroTicks + microTick] - allVoltages[n2 * totalMicroTicks + microTick];
            // Break the CEE <-> board feedback loop: never store a blown-up (Inf/NaN/1e25 V) pin voltage.
            if (VoltageGuard.isSane(u))
                sim.pinU[k] += BoardSim.PIN_RELAX * (u - sim.pinU[k]);
            else
                sim.rejectPin(k);
        }
    }
}
