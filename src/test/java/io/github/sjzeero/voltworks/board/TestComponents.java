package io.github.sjzeero.voltworks.board;

import io.github.sjzeero.voltworks.circuits.components.*;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

final class TestComponents {
    private static final Map<String, Component> CACHE = new HashMap<>();

    private TestComponents() {
    }

    private static ComponentFootprint fp(int w, int h, int... pads) {
        var b = new ComponentFootprint.Builder(w, h);
        for(int i = 0; i + 2 < pads.length + 1; i += 3)
            b.addPad(pads[i], pads[i + 1], pads[i + 2]);
        return b.build();
    }

    static Component get(String id) {
        return CACHE.computeIfAbsent(id, TestComponents::create);
    }

    private static Component create(String id) {
        return switch(id) {
            case "connector" -> new ConnectorComponent(fp(3, 3, 1, 1, 0));
            case "resistor" -> new ResistorComponent(fp(5, 3, 0, 1, 0, 4, 1, 1));
            case "diode" -> new DiodeComponent(fp(5, 3, 0, 1, 0, 4, 1, 1));
            case "capacitor" -> new CapacitorComponent(fp(3, 3, 0, 1, 0, 2, 1, 1));
            case "switch" -> new SwitchComponent(fp(4, 3, 0, 1, 0, 3, 1, 1));
            case "relay" -> new RelayComponent(fp(4, 3, 0, 0, 0, 0, 2, 1, 2, 0, 2, 3, 1, 3, 2, 2, 4));
            case "light_bulb" -> new LightBulbComponent(fp(3, 3, 0, 1, 0, 2, 1, 1));
            case "vfet" -> new VFETComponent(fp(3, 3, 0, 0, 0, 2, 0, 1, 1, 2, 2));
            case "triode" -> new ElectronTubeComponent(fp(3, 3, 0, 0, 2, 1, 1, 0, 2, 0, 1, 0, 2, 3, 2, 2, 4));
            case "fuse_holder" -> new FuseHolderComponent(fp(4, 3, 0, 1, 0, 3, 1, 1));
            case "voltage_gauge" -> new VoltageGaugeComponent(fp(5, 5, 2, 0, 0, 2, 4, 1));
            case "current_gauge" -> new CurrentGaugeComponent(fp(5, 5, 2, 0, 0, 2, 4, 1));
            case "button" -> new ButtonComponent(fp(3, 3, 0, 1, 0, 2, 1, 1));
            case "buzzer" -> new BuzzerComponent(fp(3, 3, 0, 1, 0, 2, 1, 1));
            case "potentiometer" -> new PotentiometerComponent(fp(5, 5, 1, 2, 0, 2, 3, 1, 3, 2, 2));
            default -> throw new IllegalArgumentException(id);
        };
    }

    static final Function<String, Component> FACTORY = TestComponents::get;
}
