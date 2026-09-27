package io.github.sjzeero.eecircuits.client;

import io.github.sjzeero.eecircuits.board.BoardBlockEntity;
import io.github.sjzeero.eecircuits.circuits.components.PotentiometerComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

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

    public static @Nullable Vec3 hitLocation() {
        var hit = Minecraft.getInstance().hitResult;
        if(hit == null || hit.getType() != HitResult.Type.BLOCK)
            return null;
        return hit.getLocation();
    }
}
