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
        boolean changed = false;
        if(payload.full() != null && payload.full().getInt("Version") != be.version()) {
            be.applyFull(payload.full(), level.registryAccess());
            changed = true;
        }
        if(payload.state() != null)
            changed |= be.applyState(payload.state(), level.registryAccess());
        if(be.baked() != null)
            be.baked().clientTick();
        var client = entity.getContraption().getOrCreateClientContraptionLazy();
        var renderLevel = client.getRenderLevel();
        var state = renderLevel.getBlockState(payload.pos());
        if(state.getBlock() instanceof io.github.sjzeero.voltworks.board.BoardBlock && state.getValue(io.github.sjzeero.voltworks.board.BoardBlock.LIGHT) != be.light()) {
            renderLevel.setBlock(payload.pos(), state.setValue(io.github.sjzeero.voltworks.board.BoardBlock.LIGHT, be.light()), 0);
            renderLevel.runLightEngine();
            changed = true;
        }
        if(changed)
            client.invalidateStructure();
    }

    public static void state(BoardStatePayload payload) {
        var level = Minecraft.getInstance().level;
        if(level != null && level.getBlockEntity(payload.pos()) instanceof BoardBlockEntity be)
            be.applyState(payload, level.registryAccess());
    }
}
