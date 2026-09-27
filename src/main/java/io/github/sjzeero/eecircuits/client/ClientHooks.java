package io.github.sjzeero.eecircuits.client;

import io.github.sjzeero.eecircuits.board.BoardBlockEntity;
import io.github.sjzeero.eecircuits.circuits.components.PotentiometerComponent;
import net.minecraft.client.Minecraft;

import java.util.UUID;

public final class ClientHooks {
    private ClientHooks() {
    }

    public static void openComponentScreen(BoardBlockEntity be, UUID component) {
        for(var placed : be.getSchematic().components()) {
            if(!placed.uuid.equals(component))
                continue;
            if(placed.component instanceof PotentiometerComponent)
                Minecraft.getInstance().setScreen(new PotentiometerScreen(be.getBlockPos(), placed));
            return;
        }
    }
}
