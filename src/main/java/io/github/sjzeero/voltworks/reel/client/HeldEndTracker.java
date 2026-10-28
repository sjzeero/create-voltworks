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

/**
 * Remembers where the local player's held cable end model was really drawn (fed by ItemRendererMixin with the pose
 * the item renderer uses for the model, display transform included), so the held wire can end exactly in its back.
 */
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

    /** Called by the item renderer once the model's whole pose (display transform and -0.5 centring) is applied. */
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

    /** The held model's cord point in the world, and its cord axis out of the model (unit). */
    public record Seen(Vector3d at, Vector3d out) {}

    /**
     * Where the held model's cord leaves it, for this frame's world camera, or null if it was not drawn in the
     * current or last frame for this camera mode. The axis is the captured pose's own, moved to the world the same
     * way as the point, so on screen the cable continues the cord straight out of the model.
     */
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
