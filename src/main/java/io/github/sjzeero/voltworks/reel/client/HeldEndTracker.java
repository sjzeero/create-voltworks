package io.github.sjzeero.voltworks.reel.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sjzeero.voltworks.reel.ReelPlugItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class HeldEndTracker {
    private HeldEndTracker() {}

    private record Shot(Vector3f eye, Vector3f eyeOut, Matrix4f projection, long frame) {}

    private static long frame;
    private static boolean inLevel, localPlayer;
    private static @Nullable Shot first, third;

    public static void init() {
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
                frame++;
                inLevel = true;
            } else if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL)
                inLevel = false;
        });
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Pre e) -> localPlayer = e.getEntity() == Minecraft.getInstance().player);
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Post e) -> localPlayer = false);
    }

    public static void capture(ItemStack stack, ItemDisplayContext ctx, PoseStack pose) {
        if (!(stack.getItem() instanceof ReelPlugItem))
            return;
        boolean firstPerson = ctx.firstPerson();
        boolean thirdPerson = ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (!firstPerson && !(thirdPerson && inLevel && localPlayer))
            return;
        boolean cord = ReelPlugItem.isCord(stack);
        Vector3f px = HeldEnd.backPx(cord).get(new Vector3f());
        Vector3f outPx = HeldEnd.outPx(cord).get(new Vector3f());
        Matrix4f mv = RenderSystem.getModelViewMatrix();
        Shot shot = new Shot(HeldEnd.eye(mv, pose.last().pose(), px), HeldEnd.eye(mv, pose.last().pose(), outPx),
                new Matrix4f(RenderSystem.getProjectionMatrix()), frame);
        if (firstPerson)
            first = shot;
        else
            third = shot;
    }

    public record Seen(Vector3d at, Vector3d out) {}

    public static @Nullable Seen world(boolean firstPerson, Vector3d cam, Matrix4f view, Matrix4f projection) {
        Shot s = firstPerson ? first : third;
        if (s == null || frame - s.frame > 1)
            return null;
        Vector3d at = HeldEnd.world(cam, view, HeldEnd.reproject(s.eye, s.projection, projection));
        Vector3d tip = HeldEnd.world(cam, view, HeldEnd.reproject(s.eyeOut, s.projection, projection));
        Vector3d out = tip.sub(at, new Vector3d());
        return new Seen(at, out.lengthSquared() > 1e-12 ? out.normalize() : out);
    }
}
