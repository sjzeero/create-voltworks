package io.github.sjzeero.voltworks.reel.client;

import io.github.sjzeero.voltworks.reel.CableReelBlockEntity;
import io.github.sjzeero.voltworks.reel.ReelPlugItem;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Draws the cable from the reel to the hand while its end is carried: dark while it reaches, red when stretched too far. */
public final class ReelClient {
    private ReelClient() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ReelClient::tick);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null)
            return;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ReelPlugItem))
            return;
        CableReelBlockEntity be = ReelPlugItem.reel(mc.level, held);
        if (be == null)
            return;
        Vec3 from = be.outletWorld();
        Vec3 look = player.getViewVector(1);
        Vec3 hand = player.getEyePosition().add(look.scale(0.5)).add(0, -0.35, 0);
        boolean ok = from.distanceTo(player.position().add(0, 1, 0)) <= be.stored();
        Outliner.getInstance().showLine("voltworks_reel_cable", from, hand)
                .lineWidth(1 / 16f).colored(ok ? 0x2b2d36 : 0xff4040).disableLineNormals();
    }
}
