package io.github.sjzeero.voltworks.redstone;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class RedstoneSync {
    private RedstoneSync() {
    }

    public static void sendAll(ServerPlayer player) {
        List<RedstoneWiresPayload.Wire> all = new ArrayList<>();
        for(RedstoneLinks.Link l : RedstoneLinks.get(player.serverLevel()).all())
            all.add(new RedstoneWiresPayload.Wire(l.cells, l.power));
        PacketDistributor.sendToPlayer(player, new RedstoneWiresPayload(true, all, List.of(), List.of()));
    }

    static void added(ServerLevel level, RedstoneLinks.Link link) {
        PacketDistributor.sendToPlayersInDimension(level, new RedstoneWiresPayload(false, List.of(new RedstoneWiresPayload.Wire(link.cells, link.power)), List.of(), List.of()));
    }

    static void removed(ServerLevel level, RedstoneLinks.Link link) {
        PacketDistributor.sendToPlayersInDimension(level, new RedstoneWiresPayload(false, List.of(), List.of(link.key()), List.of()));
    }

    static void powered(ServerLevel level, List<RedstoneLinks.Link> links) {
        List<RedstoneWiresPayload.Power> powers = new ArrayList<>(links.size());
        for(RedstoneLinks.Link l : links)
            powers.add(new RedstoneWiresPayload.Power(l.key(), l.power));
        PacketDistributor.sendToPlayersInDimension(level, new RedstoneWiresPayload(false, List.of(), List.of(), powers));
    }
}
