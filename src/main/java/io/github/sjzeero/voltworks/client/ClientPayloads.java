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

    public static void contraptionBoard(io.github.sjzeero.voltworks.network.ContraptionBoardPayload payload) {
        var level = Minecraft.getInstance().level;
        if(level == null || !(level.getEntity(payload.entity()) instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity entity) || entity.getContraption() == null)
            return;
        if(!(entity.getContraption().getOrCreateClientContraptionLazy().getBlockEntity(payload.pos()) instanceof BoardBlockEntity be))
            return;
        boolean changed;
        if(payload.full() != null) {
            be.applyFull(payload.full(), level.registryAccess());
            changed = true;
        } else
            changed = payload.state() != null && be.applyState(payload.state(), level.registryAccess());
        if(changed)
            entity.getContraption().getOrCreateClientContraptionLazy().invalidateStructure();
    }

    public static void state(BoardStatePayload payload) {
        var level = Minecraft.getInstance().level;
        if(level != null && level.getBlockEntity(payload.pos()) instanceof BoardBlockEntity be)
            be.applyState(payload, level.registryAccess());
    }
}
