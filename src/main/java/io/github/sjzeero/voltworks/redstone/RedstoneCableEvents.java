package io.github.sjzeero.voltworks.redstone;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class RedstoneCableEvents {
    private RedstoneCableEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if(e.getLevel() instanceof ServerLevel level)
                RedstoneLinks.get(level).tick();
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> sync(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> sync(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> sync(e.getEntity()));
    }

    private static void sync(net.minecraft.world.entity.player.Player player) {
        if(player instanceof ServerPlayer sp)
            RedstoneSync.sendAll(sp);
    }
}
