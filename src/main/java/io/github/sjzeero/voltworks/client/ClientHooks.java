package io.github.sjzeero.voltworks.client;

import io.github.sjzeero.voltworks.board.BoardBlockEntity;
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
            return;
        }
    }

    public static @Nullable Vec3 contraptionHit;

    public static @Nullable Vec3 hitLocation() {
        if(contraptionHit != null)
            return contraptionHit;
        var hit = Minecraft.getInstance().hitResult;
        if(hit == null || hit.getType() != HitResult.Type.BLOCK)
            return null;
        return hit.getLocation();
    }
}
