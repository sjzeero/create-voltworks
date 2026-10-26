package io.github.sjzeero.voltworks.light;

import io.github.sjzeero.voltworks.VoltageGuard;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import io.github.sjzeero.voltworks.circuits.Thermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Create: Power Grid (patryk3211) to Voltworks by sjzeero; electrical side reimplemented on CEE devices.
 * A heating filament between node 0 (+) and node 1 (-): resistance grows with temperature (cold 15, hot 100 ohm),
 * brightness follows temperature, and a filament held above the overheat temperature burns out in an explosion.
 * Rated: about 45 V (20 W). Brightness starts around 22 V, full at ~42 V, explodes above ~52 V.
 */
public class FactoryLightDevice extends SimpleElectricalDevice {
    public static final double R_COLD = 15;
    public static final double R_HOT = 100;
    public static final float T_GLOW = 1200;
    public static final float T_OVERHEAT = 1850;
    public static final float THERMAL_MASS = 0.04f;
    public static final float RATED_POWER = 20.25f;
    private static final float DISSIPATION = RATED_POWER / (1500 - 25);
    private static final float[] LEVELS = {600, 1200, 1400};

    private volatile float temperature = Float.NaN;
    private float prevTemperature;
    private int overheatTicks;
    private boolean exploded;

    public FactoryLightDevice(Level level, BlockPos pos, DevicesSavedData sd, SimulatedDeviceType<?> type) {
        super(level, pos, sd, type);
    }

    public float temperature() {
        return Float.isFinite(temperature) ? temperature : Thermal.getAmbientTemperature(level, pos);
    }

    private double resistance() {
        float amb = Thermal.getAmbientTemperature(level, pos);
        double f = (temperature() - amb) / (T_GLOW - amb);
        f = Math.max(0, Math.min(1, f));
        return R_COLD + (R_HOT - R_COLD) * f;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        bridges.builder(pos).resistor(0, 1, resistance());
        if (level.isLoaded(pos)) {
            BlockState st = level.getBlockState(pos);
            if (st.getBlock() instanceof FactoryLightBlock) {
                // rows: node 0 of this lamp is wired to node 1 of the next lamp on the same axis
                BlockPos next = pos.relative(Direction.get(Direction.AxisDirection.POSITIVE, st.getValue(FactoryLightBlock.AXIS)));
                if (level.isLoaded(next)) {
                    BlockState ns = level.getBlockState(next);
                    if (ns.getBlock() instanceof FactoryLightBlock && ns.getValue(FactoryLightBlock.AXIS) == st.getValue(FactoryLightBlock.AXIS))
                        bridges.bridge(new InWorldNode(st.getValue(FactoryLightBlock.FLIP) ? 1 : 0, pos), new InWorldNode(ns.getValue(FactoryLightBlock.FLIP) ? 0 : 1, next), ElectricalProperties.resistor(0.01));
                }
            }
        }
    }

    @Override
    public void postTick(SimulationResults results) {
        double power = VoltageGuard.powerFromSq(results.getVoltageAtSqr(pos, 0, 1), resistance());
        if (!Double.isFinite(power)) power = 0;
        thermalTick(power);
        int lvl = 0;
        float t = temperature();
        for (float th : LEVELS) if (t >= th) lvl++;
        setPower(lvl);
    }

    private void setPower(int target) {
        var server = level.getServer();
        if (server == null) return;
        server.execute(() -> {
            if (!level.isLoaded(pos)) return;
            BlockState s = level.getBlockState(pos);
            if (s.getBlock() instanceof FactoryLightBlock && s.getValue(FactoryLightBlock.POWER) != target)
                level.setBlock(pos, s.setValue(FactoryLightBlock.POWER, target), 3);
        });
    }

    private void thermalTick(double power) {
        float ambient = Thermal.getAmbientTemperature(level, pos);
        if (!Float.isFinite(temperature) || temperature < Thermal.ABSOLUTE_ZERO) {
            temperature = ambient;
            prevTemperature = ambient;
        }
        if (exploded) return;
        float temp = temperature;
        temp += (float) (power / 20f / THERMAL_MASS);
        float dissipated = DISSIPATION * (temp - ambient);
        temp -= dissipated / 20f / THERMAL_MASS;
        if (dissipated > 0 && temp < ambient) temp = ambient;
        if (!Float.isFinite(temp)) temp = ambient;
        float delta = temp - prevTemperature;
        prevTemperature = temp;
        temperature = temp;

        if (!(level instanceof ServerLevel server) || !Thermal.shouldOverheat()) return;
        if (temp >= T_OVERHEAT) {
            if (delta > 0 && overheatTicks++ >= Thermal.OVERHEAT_TICKS) {
                exploded = true;
                var at = pos;
                server.getServer().execute(() -> {
                    if (!(server.getBlockState(at).getBlock() instanceof FactoryLightBlock)) return;
                    server.destroyBlock(at, false);
                    server.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1.0f, false, Level.ExplosionInteraction.BLOCK);
                });
                return;
            } else if (delta <= 0) {
                overheatTicks = 0;
                if (temp > T_OVERHEAT + 10) temperature = T_OVERHEAT + 10;
            }
        }
        if (temp >= T_OVERHEAT - 300 && level.random.nextFloat() < (temp - T_OVERHEAT + 400) / 400f)
            server.sendParticles(ParticleTypes.SMOKE, pos.getX() + level.random.nextDouble(), pos.getY() + 0.5, pos.getZ() + level.random.nextDouble(), 1, 0, 0.05, 0, 0.01);
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof FactoryLightBlock);
    }

    @Override
    public void read(CompoundTag tag) {
        if (tag.contains("Temperature")) temperature = prevTemperature = tag.getFloat("Temperature");
    }

    @Override
    public void write(CompoundTag tag) {
        if (Float.isFinite(temperature)) tag.putFloat("Temperature", temperature);
    }
}
