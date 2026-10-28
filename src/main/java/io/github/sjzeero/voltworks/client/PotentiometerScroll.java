package io.github.sjzeero.voltworks.client;

import io.github.sjzeero.voltworks.board.BoardBlock;
import io.github.sjzeero.voltworks.board.BoardBlockEntity;
import io.github.sjzeero.voltworks.circuits.components.PotentiometerComponent;
import io.github.sjzeero.voltworks.network.UpdateComponentPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PotentiometerScroll {
    private static final double REACH = 8;

    private PotentiometerScroll() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(PotentiometerScroll::scroll);
    }

    private static void scroll(InputEvent.MouseScrollingEvent event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var level = mc.level;
        if(player == null || level == null || mc.screen != null)
            return;
        if(!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return;
        var pos = hit.getBlockPos();
        if(!(level.getBlockEntity(pos) instanceof BoardBlockEntity be))
            return;
        Vec3 origin = player.getEyePosition().subtract(pos.getX(), pos.getY(), pos.getZ());
        Vec3 target = origin.add(player.getViewVector(1f).scale(REACH));
        var part = BoardBlock.pick(be.interactables(), level.getBlockState(pos), origin, target);
        if(part == null)
            return;
        for(var placed : be.getSchematic().components()) {
            if(!placed.uuid.equals(part.uuid()))
                continue;
            if(!(placed.component instanceof PotentiometerComponent))
                return;
            double dy = event.getScrollDeltaY();
            if(dy == 0)
                return;
            event.setCanceled(true);
            int step = player.isShiftKeyDown() ? 1 : 5;
            int current = placed.get(PotentiometerComponent.VALUE);
            int value = Mth.clamp(current + (dy > 0 ? step : -step), 0, 100);
            if(value == current)
                return;
            placed.set(PotentiometerComponent.VALUE, value);
            var tag = new CompoundTag();
            tag.put(PotentiometerComponent.VALUE.id().toString(), IntTag.valueOf(value));
            PacketDistributor.sendToServer(new UpdateComponentPayload(pos, placed.uuid, PotentiometerComponent.VALUE.id(), tag));
            player.displayClientMessage(Component.literal(value + "%"), true);
            return;
        }
    }
}
