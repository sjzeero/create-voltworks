package io.github.sjzeero.voltworks.board;

import io.github.sjzeero.voltworks.circuits.components.Component;
import io.github.sjzeero.voltworks.circuits.components.LightBulbComponent;
import io.github.sjzeero.voltworks.circuits.components.NeonBulbComponent;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;

public final class BoardLights {
    private BoardLights() {
    }

    public static int lightOf(PlacedComponent placed) {
        if(placed.component instanceof LightBulbComponent)
            return placed.data(Component.FloatPair.class).map(d -> Math.round(d.value() * 15)).orElse(0);
        if(placed.has(NeonBulbComponent.LIT) && placed.get(NeonBulbComponent.LIT))
            return 7;
        return 0;
    }
}
