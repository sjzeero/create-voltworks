package io.github.sjzeero.voltworks.redstone;

import com.simibubi.create.AllSoundEvents;
import io.github.sjzeero.voltworks.VoltworksConfig;
import io.github.sjzeero.voltworks.util.Spools;
import io.github.sjzeero.voltworks.wallwire.WallCell;
import io.github.sjzeero.voltworks.wallwire.WallRouter;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;
import java.util.Locale;

public final class RedstoneWirePlacement {
    private RedstoneWirePlacement() {
    }

    public static void place(ServerPlayer player, PlaceRedstoneWirePayload p) {
        ServerLevel level = player.serverLevel();
        if(!(player.getMainHandItem().getItem() instanceof RedstoneCableItem)) {
            deny(player, "invalid", null);
            return;
        }
        List<WallCell> points = p.points();
        if(points.size() < 2 || points.size() > VoltworksConfig.wallWireMaxPoints()) {
            deny(player, "need_point", null);
            return;
        }
        WallCell first = points.getFirst(), last = points.getLast();
        if(!first.onBlockFace() || !last.onBlockFace()) {
            deny(player, "bad_face", null);
            return;
        }
        for(WallCell c : points) {
            if(!level.isLoaded(c.pos()) || !WallRouter.supports(level, c)) {
                deny(player, "no_path", c.pos());
                return;
            }
            if(!WallSpace.sameSpace(level, c.pos(), first.pos())) {
                deny(player, "invalid", c.pos());
                return;
            }
        }
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 3;
        if(WallSpace.distance(level, player.getEyePosition(), last.point()) > reach + 1) {
            deny(player, "invalid", null);
            return;
        }
        double maxLength = VoltworksConfig.wallWireMaxLength();
        List<WallCell> cells = WallRouter.route(level, points, (int) Math.floor(maxLength * 16));
        if(cells == null) {
            deny(player, "no_path", last.pos());
            return;
        }
        double length = WallRouter.pixels(cells) / 16.0;
        if(length > maxLength) {
            deny(player, "too_long", last.pos());
            return;
        }
        String err = RedstoneLinks.get(level).add(cells, length);
        if(err != null) {
            deny(player, err, last.pos());
            return;
        }
        Spools.consume(player, player.getMainHandItem());
        AllSoundEvents.WRENCH_REMOVE.playOnServer(level, BlockPos.containing(WallSpace.toWorld(level, last.pos().getCenter())));
        player.displayClientMessage(Component.translatable("voltworks.redstone_cable.linked", String.format(Locale.ROOT, "%.2f", length)), true);
    }

    private static void deny(ServerPlayer player, String key, BlockPos pos) {
        player.displayClientMessage(Component.translatable("voltworks.redstone_cable." + key), true);
        AllSoundEvents.DENY.playOnServer(player.level(), pos != null ? BlockPos.containing(WallSpace.toWorld(player.level(), pos.getCenter())) : player.blockPosition());
    }
}
