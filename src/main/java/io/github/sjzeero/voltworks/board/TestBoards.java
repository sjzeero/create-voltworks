package io.github.sjzeero.voltworks.board;

import io.github.sjzeero.voltworks.circuits.components.Component;
import io.github.sjzeero.voltworks.circuits.components.ElectronTubeComponent;
import io.github.sjzeero.voltworks.circuits.components.FuseHolderComponent;
import io.github.sjzeero.voltworks.circuits.components.FuseState;
import io.github.sjzeero.voltworks.circuits.components.RelayComponent;
import io.github.sjzeero.voltworks.circuits.components.ResistorComponent;
import io.github.sjzeero.voltworks.circuits.components.CurrentGaugeComponent;
import io.github.sjzeero.voltworks.circuits.components.VoltageGaugeComponent;
import io.github.sjzeero.voltworks.circuits.schematic.CircuitSchematic;

import java.util.List;
import java.util.function.Function;

public final class TestBoards {
    public record Supply(int pin, double volts) {
    }

    public record Preset(CircuitSchematic schematic, List<Supply> supplies) {
    }

    public static final List<String> NAMES = List.of("relay", "vfet", "triode", "fuse", "overheat", "gauge", "controls");

    private TestBoards() {
    }

    public static Preset build(String name, Function<String, Component> components) {
        var s = new BoardSketch(components);
        s.place("connector", 0, 0);
        s.place("connector", 0, 13);
        return switch(name) {
            case "relay" -> {
                s.place("switch", 4, 12);
                s.place("relay", 9, 4, RelayComponent.THRESHOLD_VOLTAGE, 9f);
                s.place("light_bulb", 12, 9);
                s.h(14, 1, 4).v(4, 13, 14);
                s.backV(7, 4, 13).backH(4, 7, 9);
                s.backV(1, 14, 15).backH(15, 1, 15).backV(15, 5, 15).backH(5, 12, 15);
                s.h(6, 3, 9).v(3, 1, 6).h(1, 1, 3);
                s.v(11, 6, 10).h(10, 11, 12);
                s.v(14, 2, 10).h(2, 3, 14);
                yield new Preset(s.schematic, List.of(new Supply(1, 12)));
            }
            case "vfet" -> {
                s.place("connector", 13, 13);
                s.place("resistor", 2, 3, ResistorComponent.RESISTANCE, 100f);
                s.place("vfet", 6, 6);
                s.v(1, 4, 14).h(4, 1, 2);
                s.v(6, 4, 6);
                s.v(8, 1, 6).h(1, 1, 8);
                s.v(7, 8, 14).h(14, 7, 14);
                yield new Preset(s.schematic, List.of(new Supply(1, 12), new Supply(2, 0)));
            }
            case "triode" -> {
                s.place("connector", 13, 13);
                s.place("resistor", 2, 3, ResistorComponent.RESISTANCE, 10_000f);
                s.place("triode", 6, 6);
                s.v(1, 4, 14).h(4, 1, 2);
                s.v(6, 4, 6);
                s.v(7, 1, 7).h(1, 1, 8).v(8, 1, 6);
                s.v(6, 8, 14).h(14, 6, 14);
                s.backH(8, 8, 12).backV(12, 0, 8).backH(0, 1, 12).backV(1, 0, 1);
                yield new Preset(s.schematic, List.of(new Supply(1, 250), new Supply(2, 6)));
            }
            case "fuse" -> {
                s.place("fuse_holder", 3, 6, FuseHolderComponent.MAX_CURRENT, 1f).set(FuseHolderComponent.STATE, FuseState.CLOSED);
                s.place("resistor", 8, 6, ResistorComponent.RESISTANCE, 5f);
                s.v(1, 7, 14).h(7, 1, 3);
                s.h(7, 6, 8);
                s.v(12, 1, 7).h(1, 1, 12);
                yield new Preset(s.schematic, List.of(new Supply(1, 4)));
            }
            case "overheat" -> {
                s.place("resistor", 5, 6, ResistorComponent.RESISTANCE, 10f);
                s.v(1, 7, 14).h(7, 1, 5);
                s.v(9, 1, 7).h(1, 1, 9);
                yield new Preset(s.schematic, List.of(new Supply(1, 20)));
            }
            case "gauge" -> {
                s.place("voltage_gauge", 4, 5, VoltageGaugeComponent.MAX_VOLTAGE, 20f);
                s.place("current_gauge", 10, 5, CurrentGaugeComponent.MAX_CURRENT, 1f);
                s.place("resistor", 8, 11, ResistorComponent.RESISTANCE, 100f);
                s.v(1, 3, 14).h(3, 1, 12).v(6, 3, 5).v(12, 3, 5);
                s.v(12, 9, 12);
                s.backH(9, 0, 6).backV(0, 1, 9).backH(1, 0, 1);
                s.backV(6, 9, 12).backH(12, 6, 8);
                yield new Preset(s.schematic, List.of(new Supply(1, 12)));
            }
            case "controls" -> {
                s.place("button", 3, 6);
                s.place("light_bulb", 8, 6);
                s.place("potentiometer", 5, 9);
                s.v(1, 7, 14).h(7, 1, 3);
                s.h(7, 5, 8);
                s.v(10, 1, 7).h(1, 1, 12);
                s.h(11, 1, 6);
                s.h(11, 8, 12).v(12, 1, 11);
                yield new Preset(s.schematic, List.of(new Supply(1, 12)));
            }
            default -> null;
        };
    }
}
