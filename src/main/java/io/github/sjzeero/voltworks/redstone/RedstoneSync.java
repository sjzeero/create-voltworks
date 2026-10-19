package io.github.sjzeero.voltworks.redstone;

import io.github.sjzeero.voltworks.wallwire.WallCell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Sends the routes of the redstone wires to the clients that draw them. */
public final class RedstoneSync {
    private RedstoneSync() {
    }

    public static void sendAll(ServerPlayer player) {
        List<List<WallCell>> all = new ArrayList<>();
        for(RedstoneLinks.Link l : RedstoneLinks.get(player.serverLevel()).all())
            all.add(l.cells);
        PacketDistributor.sendToPlayer(player, new RedstoneWiresPayload(true, all, List.of()));
    }

    static void added(ServerLevel level, RedstoneLinks.Link link) {
        PacketDistributor.sendToPlayersInDimension(level, new RedstoneWiresPayload(false, List.of(link.cells), List.of()));
    }

    static void removed(ServerLevel level, RedstoneLinks.Link link) {
        PacketDistributor.sendToPlayersInDimension(level, new RedstoneWiresPayload(false, List.of(), List.of(link.key())));
    }
}
