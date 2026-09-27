package io.github.sjzeero.eecircuits.board;

import io.github.sjzeero.eecircuits.circuits.components.Component;
import io.github.sjzeero.eecircuits.circuits.components.properties.ComponentProperty;
import io.github.sjzeero.eecircuits.circuits.schematic.CircuitSchematic;
import io.github.sjzeero.eecircuits.circuits.schematic.PlacedComponent;

import java.util.function.Function;

public class BoardSketch {
    public final CircuitSchematic schematic = new CircuitSchematic();
    private final Function<String, Component> components;

    public BoardSketch(Function<String, Component> components) {
        this.components = components;
    }

    public PlacedComponent place(String id, int x, int y) {
        schematic.placeComponent(new PlacedComponent(components.apply(id), x, y, null), x, y);
        return schematic.components().get(schematic.components().size() - 1);
    }

    public <T> PlacedComponent place(String id, int x, int y, ComponentProperty<T> property, T value) {
        var placed = place(id, x, y);
        placed.set(property, value);
        return placed;
    }

    public BoardSketch h(int y, int x1, int x2) {
        schematic.front().addHorizontalLine(y, Math.min(x1, x2), Math.max(x1, x2));
        return this;
    }

    public BoardSketch v(int x, int y1, int y2) {
        schematic.front().addVerticalLine(x, Math.min(y1, y2), Math.max(y1, y2));
        return this;
    }

    public BoardSketch backH(int y, int x1, int x2) {
        schematic.back().addHorizontalLine(y, Math.min(x1, x2), Math.max(x1, x2));
        return this;
    }

    public BoardSketch backV(int x, int y1, int y2) {
        schematic.back().addVerticalLine(x, Math.min(y1, y2), Math.max(y1, y2));
        return this;
    }
}
