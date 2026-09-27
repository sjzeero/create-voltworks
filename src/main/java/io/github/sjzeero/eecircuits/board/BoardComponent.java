package io.github.sjzeero.eecircuits.board;

public interface BoardComponent {
    int nodeCount();

    void bake(PlacedPart placed, BoardCircuitBuilder builder);
}
