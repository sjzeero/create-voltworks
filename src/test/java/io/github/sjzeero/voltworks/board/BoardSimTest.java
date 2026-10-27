package io.github.sjzeero.voltworks.board;

import io.github.sjzeero.voltworks.circuits.circuitboard.BakedCircuit;
import io.github.sjzeero.voltworks.circuits.components.*;
import io.github.sjzeero.voltworks.circuits.schematic.BoardContext;
import io.github.sjzeero.voltworks.circuits.schematic.CircuitSchematic;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class BoardSimTest {
    static final BoardContext CONTEXT = new BoardContext() {
        @Override
        public Level level() {
            return null;
        }

        @Override
        public BlockPos pos() {
            return BlockPos.ZERO;
        }
    };

    static final class Rig {
        final BakedCircuit baked;
        final BoardSim sim;
        int ticks;

        Rig(CircuitSchematic schematic) {
            baked = BakedCircuit.from(schematic, CONTEXT);
            baked.setAmbientTemperature(22f);
            sim = new BoardSim(baked, null);
        }

        Rig(TestBoards.Preset preset) {
            this(preset.schematic());
            for(var supply : preset.supplies())
                pin(supply.pin(), supply.volts());
        }

        Rig pin(int k, double volts) {
            sim.pinU[k] = volts;
            return this;
        }

        Rig tick(int n) {
            for(int i = 0; i < n; i++) {
                sim.standaloneTick(1);
                baked.tick(1f);
                ticks++;
            }
            return this;
        }

        PlacedComponent part(String id) {
            for(var placed : baked.components)
                if(placed.component == TestComponents.get(id))
                    return placed;
            throw new AssertionError(id);
        }

        float temperature(PlacedComponent placed) {
            float max = Float.NEGATIVE_INFINITY;
            for(var unit : baked.thermalUnits)
                if(unit.getId().equals(placed.uuid))
                    max = Math.max(max, unit.getTemperature());
            return max;
        }
    }

    static TestBoards.Preset preset(String name) {
        return TestBoards.build(name, TestComponents.FACTORY);
    }

    static void log(String fmt, Object... args) {
        System.out.println("[board] " + String.format(Locale.ROOT, fmt, args));
    }

    private static CircuitSchematic twoPin(java.util.function.Consumer<BoardSketch> body) {
        var s = new BoardSketch(TestComponents.FACTORY);
        s.place("connector", 0, 0);
        s.place("connector", 0, 13);
        body.accept(s);
        return s.schematic;
    }

    @Test
    void resistor() {
        var rig = new Rig(twoPin(s -> {
            s.place("resistor", 5, 6, ResistorComponent.RESISTANCE, 100f);
            s.v(1, 7, 14).h(7, 1, 5);
            s.v(9, 1, 7).h(1, 1, 9);
        })).pin(1, 10).tick(2);
        double i = rig.part("resistor").wires.get(0).current();
        log("resistor I=%.6f", i);
        assertEquals(2, rig.sim.pins);
        assertEquals(0.1, Math.abs(i), 1e-3);
        assertEquals(0.1, Math.abs(rig.sim.pinCurrent(1)), 1e-3);
    }

    @Test
    void floatingRelayDoesNotBreakCircuit() {
        var rig = new Rig(twoPin(s -> {
            s.place("resistor", 5, 6, ResistorComponent.RESISTANCE, 100f);
            s.place("relay", 6, 10);
            s.v(1, 7, 14).h(7, 1, 5);
            s.v(9, 1, 7).h(1, 1, 9);
        })).pin(1, 10).tick(20);
        double i = rig.part("resistor").wires.get(0).current();
        log("floating relay: resistor I=%.6f conv=%s bad=%d", i, rig.sim.converged, rig.sim.badSteps);
        assertTrue(rig.sim.converged);
        assertEquals(0.1, Math.abs(i), 1e-3);
    }

    @Test
    void diodeForward() {
        var rig = new Rig(twoPin(s -> {
            s.place("resistor", 2, 6, ResistorComponent.RESISTANCE, 100f);
            s.place("diode", 9, 6);
            s.v(1, 7, 14).h(7, 1, 2);
            s.h(7, 6, 9).v(13, 1, 7).h(1, 1, 13);
        })).pin(1, -5).tick(20);
        double i = rig.part("resistor").wires.get(0).current();
        log("diode I=%.6f conv=%s", i, rig.sim.converged);
        assertTrue(rig.sim.converged);
        assertEquals(0.04278, Math.abs(i), 2e-4);
    }

    @Test
    void rcCharge() {
        var rig = new Rig(twoPin(s -> {
            s.place("resistor", 2, 6, ResistorComponent.RESISTANCE, 1000f);
            s.place("capacitor", 9, 6, CapacitorComponent.CAPACITANCE, 0.001f);
            s.v(1, 7, 14).h(7, 1, 2);
            s.h(7, 6, 9).v(11, 1, 7).h(1, 1, 11);
        })).pin(1, 10);
        rig.tick(20);
        double v = Math.abs(rig.part("capacitor").wires.get(0).potentialDifference());
        log("rc v20=%.4f", v);
        assertEquals(6.04, v, 0.05);
    }

    private Rig buzzerRig(double supply) {
        return new Rig(twoPin(s -> {
            s.place("resistor", 2, 6, ResistorComponent.RESISTANCE, 100f);
            s.place("buzzer", 9, 6);
            s.v(1, 7, 14).h(7, 1, 2);
            s.h(7, 6, 9).v(11, 1, 7).h(1, 1, 11);
        })).pin(1, supply).tick(5);
    }

    @Test
    void buzzerIs500OhmLoad() {
        var rig = buzzerRig(24);
        var buzzer = rig.part("buzzer");
        double v = Math.abs(buzzer.wires.get(0).potentialDifference());
        double i = Math.abs(buzzer.wires.get(0).current());
        log("buzzer 24V supply: V=%.4f I=%.6f", v, i);
        assertEquals(20.0, v, 0.05);
        assertEquals(0.04, i, 1e-4);
        assertTrue(BuzzerComponent.sounds(v));
        assertEquals(0.5 + 20 / 48.0, BuzzerComponent.pitchFor(v), 1e-3);
    }

    @Test
    void buzzerSilentBelowThreshold() {
        var rig = buzzerRig(12);
        double v = Math.abs(rig.part("buzzer").wires.get(0).potentialDifference());
        log("buzzer 12V supply: V=%.4f", v);
        assertEquals(10.0, v, 0.05);
        assertFalse(BuzzerComponent.sounds(v));
        assertTrue(BuzzerComponent.sounds(12.0));
        assertTrue(BuzzerComponent.sounds(-30.0));
    }

    @Test
    void buzzerPitchFollowsVoltage() {
        assertEquals(0.75f, BuzzerComponent.pitchFor(12), 1e-4);
        assertEquals(1.0f, BuzzerComponent.pitchFor(24), 1e-4);
        assertEquals(1.5f, BuzzerComponent.pitchFor(48), 1e-4);
        assertEquals(2.0f, BuzzerComponent.pitchFor(500), 1e-4);
        assertTrue(BuzzerComponent.pitchFor(30) > BuzzerComponent.pitchFor(20));
        // re-trigger interval keeps the shortened chunk continuous (chunk length / pitch, floored)
        assertEquals(13, BuzzerComponent.intervalFor(0.75f));
        assertEquals(10, BuzzerComponent.intervalFor(1.0f));
        assertEquals(6, BuzzerComponent.intervalFor(1.5f));
    }

    private static CircuitSchematic buttonBoard() {
        return twoPin(s -> {
            s.place("resistor", 2, 6, ResistorComponent.RESISTANCE, 100f);
            s.place("button", 9, 6);
            s.v(1, 7, 14).h(7, 1, 2);
            s.h(7, 6, 9).v(11, 1, 7).h(1, 1, 11);
        });
    }

    @Test
    void buttonReleasesAfterPress() {
        int[] notified = {0};
        var context = new BoardContext() {
            @Override
            public Level level() {
                return null;
            }

            @Override
            public BlockPos pos() {
                return BlockPos.ZERO;
            }

            @Override
            public void propertyChanged(PlacedComponent component, net.minecraft.resources.ResourceLocation property) {
                if(property.equals(ButtonComponent.STATE.id()))
                    notified[0]++;
            }
        };
        var baked = BakedCircuit.from(buttonBoard(), context);
        baked.setAmbientTemperature(22f);
        var sim = new BoardSim(baked, null);
        sim.pinU[1] = 10;
        PlacedComponent button = null;
        for(var placed : baked.components)
            if(placed.component == TestComponents.get("button"))
                button = placed;
        assertNotNull(button);
        var resistor = baked.components.stream().filter(p -> p.component == TestComponents.get("resistor")).findFirst().orElseThrow();
        Runnable tick = () -> { sim.standaloneTick(1); baked.tick(1f); };

        for(int i = 0; i < 3; i++) tick.run();
        assertEquals(0, resistor.wires.get(0).current(), 1e-6, "released button must be open");

        ((ButtonComponent) button.component).use(context, button, null);
        assertEquals(ButtonComponent.PRESS_TICKS, button.get(ButtonComponent.STATE));
        assertEquals(1, notified[0]);
        assertEquals(io.github.sjzeero.voltworks.Voltworks.asResource("button_on"), button.component.getModelId(button));
        tick.run();
        tick.run();
        double on = Math.abs(resistor.wires.get(0).current());
        log("button pressed: I=%.6f state=%d", on, button.get(ButtonComponent.STATE));
        assertEquals(0.1, on, 1e-3);

        for(int i = 0; i < ButtonComponent.PRESS_TICKS + 2; i++) tick.run();
        double off = Math.abs(resistor.wires.get(0).current());
        log("button released: I=%.6f state=%d notified=%d", off, button.get(ButtonComponent.STATE), notified[0]);
        assertEquals(0, button.get(ButtonComponent.STATE));
        assertEquals(0, off, 1e-6);
        assertEquals(2, notified[0], "release must be pushed to clients right away");
        assertEquals(io.github.sjzeero.voltworks.Voltworks.asResource("button"), button.component.getModelId(button));
    }

    @Test
    void buttonClientMirrorsServerState() {
        var client = new BoardContext() {
            @Override
            public Level level() {
                return null;
            }

            @Override
            public BlockPos pos() {
                return BlockPos.ZERO;
            }

            @Override
            public boolean isClient() {
                return true;
            }
        };
        var baked = BakedCircuit.from(buttonBoard(), client);
        var button = baked.components.stream().filter(p -> p.component == TestComponents.get("button")).findFirst().orElseThrow();
        // state packet "pressed" arrives
        button.set(ButtonComponent.STATE, ButtonComponent.PRESS_TICKS);
        var modelBefore = button.component.getModelId(button);
        for(int i = 0; i < 40; i++)
            baked.clientTick();
        // the client must not count down on its own, otherwise the later "released" packet is a no-op
        // and the board model is never rebuilt (button stuck pressed)
        assertEquals(ButtonComponent.PRESS_TICKS, button.get(ButtonComponent.STATE));
        button.set(ButtonComponent.STATE, 0);
        assertNotEquals(modelBefore, button.component.getModelId(button));
    }

    @Test
    void relaySwitchesLoad() {
        var rig = new Rig(preset("relay")).tick(20);
        var relay = rig.part("relay");
        var bulb = rig.part("light_bulb");
        double off = bulb.wires.get(0).current();
        log("relay off: state=%s bulbI=%.6f", relay.get(RelayComponent.STATE), off);
        assertFalse(((io.github.sjzeero.voltworks.sim.special.RelaySwitchWire) relay.wires.get(1)).getState());
        assertEquals(0, off, 1e-6);

        var sw = rig.part("switch");
        ((SwitchComponent) sw.component).use(CONTEXT, sw, null);
        rig.tick(40);
        double on = Math.abs(bulb.wires.get(0).current());
        float t = rig.temperature(bulb);
        log("relay on: NO=%s bulbI=%.6f bulbT=%.1f light=%d", ((io.github.sjzeero.voltworks.sim.special.RelaySwitchWire) relay.wires.get(1)).getState(), on, t, BoardLights.lightOf(bulb));
        assertTrue(((io.github.sjzeero.voltworks.sim.special.RelaySwitchWire) relay.wires.get(1)).getState());
        assertTrue(on > 0.2 && on < 0.6, "bulb current " + on);
        assertTrue(BoardLights.lightOf(bulb) > 0);

        ((SwitchComponent) sw.component).use(CONTEXT, sw, null);
        rig.tick(40);
        log("relay released: bulbI=%.6f", bulb.wires.get(0).current());
        assertEquals(0, bulb.wires.get(0).current(), 1e-6);
    }

    @Test
    void fuseBlows() {
        var rig = new Rig(preset("fuse")).tick(20);
        var fuse = rig.part("fuse_holder");
        double i = Math.abs(rig.part("resistor").wires.get(0).current());
        log("fuse at 4 V: state=%s I=%.5f", fuse.get(FuseHolderComponent.STATE), i);
        assertEquals(FuseState.CLOSED, fuse.get(FuseHolderComponent.STATE));
        assertEquals(0.8, i, 0.01);
        rig.pin(1, 12);
        int blownAt = -1;
        for(int n = 0; n < 200 && blownAt < 0; n++) {
            rig.tick(1);
            if(fuse.get(FuseHolderComponent.STATE) == FuseState.BLOWN)
                blownAt = n + 1;
        }
        rig.tick(5);
        double after = Math.abs(rig.part("resistor").wires.get(0).current());
        log("fuse at 12 V: blown after %d ticks, I=%.3g", blownAt, after);
        assertTrue(blownAt > 0);
        assertEquals(0, after, 1e-6);
    }

    @Test
    void resistorOverheats() {
        var rig = new Rig(preset("overheat"));
        var r = rig.part("resistor");
        float prev = 0;
        int burntAt = -1;
        StringBuilder trace = new StringBuilder();
        for(int n = 0; n < 2000 && burntAt < 0; n++) {
            rig.tick(1);
            float t = rig.temperature(r);
            trace.append(String.format(Locale.ROOT, " %d:%.1f", n + 1, t));
            if(n > 2)
                assertTrue(t >= prev - 1e-3, "temperature fell at " + n);
            prev = t;
            if(r.destroyed)
                burntAt = n + 1;
        }
        rig.tick(3);
        log("overheat trace%s", trace);
        log("resistor burnt after %d ticks at T=%.1f, I after=%.3g", burntAt, prev, r.wires.get(0).current());
        assertTrue(burntAt > 0);
        assertEquals(0, rig.sim.pinCurrent(1), 1e-6);
    }

    @Test
    void vfetGateControl() {
        var rig = new Rig(preset("vfet")).tick(20);
        double i0 = Math.abs(rig.part("resistor").wires.get(0).current());
        rig.pin(2, -0.5).tick(20);
        double i5 = Math.abs(rig.part("resistor").wires.get(0).current());
        rig.pin(2, -5).tick(20);
        double i20 = Math.abs(rig.part("resistor").wires.get(0).current());
        rig.pin(2, 2).tick(20);
        double ip = Math.abs(rig.part("resistor").wires.get(0).current());
        log("vfet Id: Vg=0 %.5f, Vg=-0.5 %.5f, Vg=-5 %.3g, Vg=+2 %.5f", i0, i5, i20, ip);
        assertTrue(i0 > i5 && i5 > i20 && ip > i0);
        assertTrue(i20 < 1e-4);
    }

    @Test
    void triodeWarmsUp() {
        var rig = new Rig(preset("triode"));
        var tube = rig.part("triode");
        StringBuilder trace = new StringBuilder();
        double first = -1;
        for(int n = 0; n < 400; n++) {
            rig.tick(1);
            double ia = Math.abs(rig.part("resistor").wires.get(0).current());
            if(n == 0)
                first = ia;
            if(n < 40 || n % 100 == 0)
                trace.append(String.format(Locale.ROOT, " %d:T=%.0f,Ia=%.5f", n, rig.temperature(tube), ia));
        }
        double last = Math.abs(rig.part("resistor").wires.get(0).current());
        log("triode%s end Ia=%.5f", trace, last);
        assertTrue(last > 1e-3, "plate current " + last);
        assertTrue(last > first * 5 + 1e-4);
    }

    @Test
    void gaugesRead() {
        var rig = new Rig(preset("gauge")).tick(10);
        var vg = rig.part("voltage_gauge");
        var cg = rig.part("current_gauge");
        float v = vg.get(GaugeComponent.READING);
        float i = cg.get(GaugeComponent.READING);
        log("gauges: V=%.4f I=%.5f levels %d %d", v, i, vg.get(GaugeComponent.LEVEL), cg.get(GaugeComponent.LEVEL));
        assertEquals(12, v, 0.01);
        assertEquals(0.12, i, 0.002);
    }
}
