package io.github.sjzeero.eecircuits.circuits.editor;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import io.github.sjzeero.eecircuits.circuits.Thermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CircuitDesignTableDevice extends SimpleElectricalDevice {
    public static final double RESISTANCE = 480;
    public static final float REQUIRED_POWER = 30;

    // Same thermal values as Power Grid's table: 50 W max power, 0.5 thermal mass, 175 C overheat.
    public static final float THERMAL_MASS = 0.5f;
    public static final float MAX_POWER = 50;
    public static final float OVERHEAT_TEMPERATURE = 175;
    private static final float DISSIPATION = Thermal.dissipationFactor(MAX_POWER, OVERHEAT_TEMPERATURE - 25);

    private volatile double power;
    private float temperature = Float.NaN;
    private float prevTemperature;
    private int overheatTicks;
    private boolean exploded;

    public CircuitDesignTableDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    public double power() {
        return power;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        bridges.bridge(new InWorldNode(0, pos), new InWorldNode(1, pos), ElectricalProperties.resistor(RESISTANCE));
    }

    @Override
    public void postTick(SimulationResults results) {
        power = results.getVoltageAtSqr(pos, 0, 1) / RESISTANCE;
        thermalTick();
    }

    public float temperature() {
        return Float.isFinite(temperature) ? temperature : Thermal.getAmbientTemperature(level, pos);
    }

    private void thermalTick() {
        float ambient = Thermal.getAmbientTemperature(level, pos);
        if(!Float.isFinite(temperature) || temperature < Thermal.ABSOLUTE_ZERO) {
            temperature = ambient;
            prevTemperature = ambient;
        }
        if(!Thermal.shouldOverheat() || exploded) {
            temperature = ambient;
            return;
        }
        if(Double.isFinite(power))
            temperature += (float) (power / 20f / THERMAL_MASS);
        float dissipated = DISSIPATION * (temperature - ambient);
        temperature -= dissipated / 20f / THERMAL_MASS;
        if(dissipated > 0 && temperature < ambient)
            temperature = ambient;
        if(!Float.isFinite(temperature)) {
            temperature = ambient;
            prevTemperature = ambient;
        }
        float delta = temperature - prevTemperature;
        prevTemperature = temperature;

        if(!(level instanceof ServerLevel server))
            return;
        if(temperature >= OVERHEAT_TEMPERATURE) {
            if(delta > 0 && overheatTicks++ >= Thermal.OVERHEAT_TICKS) {
                exploded = true;
                var at = pos;
                server.getServer().execute(() -> {
                    if(!(server.getBlockState(at).getBlock() instanceof CircuitDesignTableBlock))
                        return;
                    server.destroyBlock(at, false);
                    server.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1.0f, false, Level.ExplosionInteraction.BLOCK);
                });
                return;
            } else if(delta <= 0) {
                overheatTicks = 0;
                if(temperature > OVERHEAT_TEMPERATURE + 10)
                    temperature = OVERHEAT_TEMPERATURE + 10;
            }
        }
        if(temperature >= OVERHEAT_TEMPERATURE - 50) {
            float chance = (temperature - OVERHEAT_TEMPERATURE + 100) / 100;
            if(level.random.nextFloat() < chance)
                server.sendParticles(ParticleTypes.SMOKE, pos.getX() + level.random.nextDouble(), pos.getY() + 1.0, pos.getZ() + level.random.nextDouble(), 1, 0, 0.05, 0, 0.01);
        }
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof CircuitDesignTableBlock);
    }

    @Override
    public void read(CompoundTag tag) {
        if(tag.contains("Temperature"))
            temperature = prevTemperature = tag.getFloat("Temperature");
    }

    @Override
    public void write(CompoundTag tag) {
        if(Float.isFinite(temperature))
            tag.putFloat("Temperature", temperature);
    }
}
