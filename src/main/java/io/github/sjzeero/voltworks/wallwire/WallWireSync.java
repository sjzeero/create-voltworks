package io.github.sjzeero.voltworks.wallwire;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class WallWireSync {
    private WallWireSync() {
    }

    public static void sendAll(ServerPlayer player) {
        WallWireData data = WallWireData.get(player.serverLevel());
        PacketDistributor.sendToPlayer(player, new WallRoutesPayload(true, new ArrayList<>(data.all()), List.of()));
    }

    static void added(ServerLevel level, WallRoute route) {
        PacketDistributor.sendToPlayersInDimension(level, new WallRoutesPayload(false, List.of(route), List.of()));
    }

    static void removed(ServerLevel level, InWorldNodeConnection connection) {
        PacketDistributor.sendToPlayersInDimension(level, new WallRoutesPayload(false, List.of(), List.of(connection)));
    }
}
