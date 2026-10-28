package io.github.sjzeero.voltworks.cord;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sjzeero.voltworks.reel.ReelAssetsTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class CordPlugModelTest {
    @Test
    void cordEntersTheMiddleOfThePlugsBack() throws IOException {
        JsonObject model = ReelAssetsTest.json("assets/voltworks/models/item/cord_plug.json");
        double maxX = -1;
        JsonObject back = null;
        for (JsonElement el : model.getAsJsonArray("elements")) {
            JsonObject e = el.getAsJsonObject();
            double x = e.getAsJsonArray("to").get(0).getAsDouble();
            if (x > maxX) {
                maxX = x;
                back = e;
            }
        }
        assertNotNull(back);
        double[] from = new double[3], to = new double[3];
        for (int i = 0; i < 3; i++) {
            from[i] = back.getAsJsonArray("from").get(i).getAsDouble();
            to[i] = back.getAsJsonArray("to").get(i).getAsDouble();
        }
        assertEquals(12, to[0], "back face at x = 12");
        assertEquals(4, (from[1] + to[1]) / 2, "centred in height");
        assertEquals(8, (from[2] + to[2]) / 2, "centred across");
        assertEquals(2, to[2] - from[2]);
        assertEquals(2, to[1] - from[1]);
    }
}
