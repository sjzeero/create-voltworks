package io.github.sjzeero.voltworks.wallwire;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class WallWireEvents {
    private WallWireEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(WallWireEvents::levelTick);
        NeoForge.EVENT_BUS.addListener(WallWireEvents::neighbourNotify);
        NeoForge.EVENT_BUS.addListener(WallWireEvents::login);
        NeoForge.EVENT_BUS.addListener(WallWireEvents::changedDimension);
        NeoForge.EVENT_BUS.addListener(WallWireEvents::respawn);
    }

    private static void levelTick(LevelTickEvent.Post event) {
        if(event.getLevel() instanceof ServerLevel level)
            WallWireData.get(level).tick();
    }

    private static void neighbourNotify(BlockEvent.NeighborNotifyEvent event) {
        if(event.getLevel() instanceof ServerLevel level)
            WallWireData.get(level).blockChanged(event.getPos());
    }

    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer player)
            WallWireSync.sendAll(player);
    }

    private static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if(event.getEntity() instanceof ServerPlayer player)
            WallWireSync.sendAll(player);
    }

    private static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if(event.getEntity() instanceof ServerPlayer player)
            WallWireSync.sendAll(player);
    }
}
