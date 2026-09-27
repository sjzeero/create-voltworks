package io.github.sjzeero.eecircuits.board;

public interface BoardComponent {
    int padCount();

    void bake(PlacedPart placed, BoardCircuitBuilder builder);

    default void save(PlacedPart placed) {
    }
}
