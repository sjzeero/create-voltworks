package io.github.sjzeero.voltworks.circuits.components;

import com.google.common.collect.ImmutableCollection;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.board.BoardBlock;
import io.github.sjzeero.voltworks.circuits.Unit;
import io.github.sjzeero.voltworks.circuits.circuitboard.ComponentCircuitBuilder;
import io.github.sjzeero.voltworks.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.voltworks.circuits.components.properties.ConstantProperty;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import io.github.sjzeero.voltworks.circuits.thermal.ThermalBuilder;
import io.github.sjzeero.voltworks.registry.VoltworksSounds;
import io.github.sjzeero.voltworks.sim.ElectricWire;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class BuzzerComponent extends OrientableComponent implements IGoggleLabel {
    public static final float RESISTANCE = 500f;
    public static final float THRESHOLD = 12f;
    public static final float MAX_POWER = 5f;
    public static final int CHUNK_TICKS = 10;
    public static final float VOLUME = 0.6f;

    public static final ConstantProperty SOUND_VOLTAGE = new ConstantProperty(Voltworks.MOD_ID, "buzzer_threshold",
            Unit.VOLTAGE.formatWithPrefixes(THRESHOLD).component());

    public BuzzerComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(LABEL, SOUND_VOLTAGE, power(MAX_POWER));
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, ThermalBuilder.@NotNull IEmitter thermals) {
        var wire = new ElectricWire(RESISTANCE, builder.terminalNode(0), builder.terminalNode(1));
        builder.add(wire);
        placed.add(wire);
        placed.customData = new State();

        thermals.builder()
                .addHeatSource(wire)
                .setThermalMass(0.002f)
                .setMaxPower(MAX_POWER, 125f);
    }

    public static float pitchFor(double voltage) {
        return (float) Mth.clamp(0.5 + voltage / 48.0, 0.5, 2.0);
    }

    public static boolean sounds(double voltage) {
        return Double.isFinite(voltage) && Math.abs(voltage) >= THRESHOLD;
    }

    public static int intervalFor(float pitch) {
        return Math.max(3, (int) Math.floor(CHUNK_TICKS / pitch));
    }

    @Override
    public boolean tick(@NotNull PlacedComponent placed) {
        if(placed.isClient() || placed.wires.isEmpty() || placed.destroyed)
            return true;
        if(!(placed.customData instanceof State state))
            return true;
        if(!(placed.getWorld() instanceof ServerLevel level))
            return true;

        double v = Math.abs(placed.wires.get(0).potentialDifference());
        long now = level.getGameTime();
        if(!sounds(v)) {
            state.nextPlay = 0;
            return true;
        }
        if(now < state.nextPlay)
            return true;

        float pitch = pitchFor(v);
        state.nextPlay = now + intervalFor(pitch);
        var pos = placed.getExactPos();
        var blockPos = placed.getPos();
        level.getServer().execute(() -> {
            if(!level.isLoaded(blockPos) || !(level.getBlockState(blockPos).getBlock() instanceof BoardBlock))
                return;
            level.playSound(null, pos.x, pos.y, pos.z, VoltworksSounds.BUZZER.get(), SoundSource.BLOCKS, VOLUME, pitch);
        });
        return true;
    }

    private static final class State {
        long nextPlay;
    }
}
