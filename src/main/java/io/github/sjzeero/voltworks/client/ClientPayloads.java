package io.github.sjzeero.voltworks.client;

import io.github.sjzeero.voltworks.board.BoardBlockEntity;
import io.github.sjzeero.voltworks.network.BoardSchematicPayload;
import io.github.sjzeero.voltworks.network.BoardStatePayload;
import net.minecraft.client.Minecraft;

public final class ClientPayloads {
    private ClientPayloads() {
    }

    public static void schematic(BoardSchematicPayload payload) {
        var level = Minecraft.getInstance().level;
        if(level != null && level.getBlockEntity(payload.pos()) instanceof BoardBlockEntity be)
            be.applyFull(payload.tag(), level.registryAccess());
    }

    public static void state(BoardStatePayload payload) {
        var level = Minecraft.getInstance().level;
        if(level != null && level.getBlockEntity(payload.pos()) instanceof BoardBlockEntity be)
            be.applyState(payload, level.registryAccess());
    }
}
