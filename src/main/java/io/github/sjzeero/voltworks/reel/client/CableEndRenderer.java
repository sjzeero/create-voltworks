package io.github.sjzeero.voltworks.reel.client;

import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sjzeero.voltworks.reel.CableEndEntity;
import io.github.sjzeero.voltworks.reel.CableReelBlock;
import io.github.sjzeero.voltworks.reel.CableReelBlockEntity;
import io.github.sjzeero.voltworks.reel.LyingEnd;
import io.github.sjzeero.voltworks.reel.ReelMath;
import io.github.sjzeero.voltworks.reel.ReelPlugItem;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sjzeero.voltworks.Voltworks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.List;

/**
 * A lying cable end: the 3D end model lying on the ground along the cable (the socket plug for the cord reel, a copper
 * lug for the cable reel), and the cable from the reel outlet into the back of it, drawn with Electro Energetics' wire
 * renderer and the reel's wire type like the end carried in the hand. A cable lying on the floor does not hang below
 * the floor: no point of it goes lower than its lower end.
 */
public class CableEndRenderer extends EntityRenderer<CableEndEntity> {
    private final ItemRenderer items;
    private final ItemStack plug = ReelPlugItem.look(true), lug = ReelPlugItem.look(false);

    public CableEndRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        items = ctx.getItemRenderer();
        shadowRadius = 0.1f;
    }

    @Override
    public boolean shouldRender(CableEndEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true; // the cable reaches far outside the end's own little box
    }

    /**
     * How an end model lies on the floor. {@code base} turns the model about its centre first, after which its lowest
     * point is at {@code floorPx}, the cable leaves along {@code axis} (radians from +Z towards +X) out of the middle
     * of the back face at {@code backPx}. The wire is drawn up to exactly there.
     */
    @Override
    public void render(CableEndEntity entity, float entityYaw, float pt, PoseStack pose, MultiBufferSource buffer, int light) {
        Level level = entity.level();
        Vec3 at = entity.getPosition(pt); // the dispatcher put the pose origin here (same interpolation)
        CableReelBlockEntity be = level.isLoaded(entity.reelPos()) && level.getBlockEntity(entity.reelPos()) instanceof CableReelBlockEntity r ? r : null;
        boolean cord = be != null && be.block().cord;

        Vec3 from = null;
        if (be != null) {
            CableReelBlock block = be.block();
            Vec3 local = block.localNode(be.getBlockState(), block.outletId());
            from = WallSpace.toWorld(WallSpace.renderPose(level, be.getBlockPos(), pt), Vec3.atLowerCornerOf(be.getBlockPos()).add(local));
        }
        // lie along the cable: the back of the end towards the reel
        LyingEnd.Lie lie = cord ? LyingEnd.PLUG : LyingEnd.LUG;
        double fallback = Math.toRadians(-entity.getYRot());
        double yaw = from == null ? fallback - lie.axis() : LyingEnd.yaw(lie, from.x - at.x, from.z - at.z, fallback);
        Matrix4d model = LyingEnd.pose(lie, yaw);

        // The baked model's quads straight through LyingEnd's matrix: no ItemRenderer.render, so no display transform
        // (or any other context handling) can sit between the matrix the cable uses and the one the model is drawn with.
        ItemStack stack = cord ? plug : lug;
        BakedModel baked = items.getModel(stack, level, null, entity.getId());
        pose.pushPose();
        pose.mulPose(new Matrix4f(model));
        pose.translate(-0.5f, -0.5f, -0.5f); // model pixels / 16 are 0..1; LyingEnd works about the model centre
        for (BakedModel pass : baked.getRenderPasses(stack, true))
            for (RenderType rt : pass.getRenderTypes(stack, true))
                items.renderModelLists(pass, stack, light, OverlayTexture.NO_OVERLAY, pose, buffer.getBuffer(rt));
        pose.popPose();

        Vector3d back = LyingEnd.toEntity(model, lie.backPx()); // same matrix as the model: the cable meets its back
        if (Minecraft.getInstance().getDebugOverlay().showDebugScreen())
            debug(pose, buffer, at, back, from);
        log(entity, cord, yaw, at, back, from, be);
        WireType type = be == null ? null : be.wireType();
        if (type != null) {
            Vec3 to = at.add(back.x, back.y, back.z);
            double distance = from.distanceTo(to);
            if (distance > 0.05) {
                WireData look = WireData.ofLength(type, ReelMath.followLength(distance, Math.max(be.stored(), distance)));
                // a cable lying on the floor does not hang through it
                // straight out of the back along the cord axis, then the curve to the reel
                List<Vec3> points = ReelWire.points(from, to, LyingEnd.direction(model, lie.backDir()), look.getSag(distance),
                        Math.min(from.y, to.y));
                pose.pushPose();
                pose.translate(-at.x, -at.y, -at.z); // entity space -> world space relative to the camera
                ReelWire.render(points, pose, buffer, type, level);
                pose.popPose();
            }
        }
        super.render(entity, entityYaw, pt, pose, buffer, light);
    }

    private static final java.util.Map<Integer, Long> LOGGED = new java.util.HashMap<>();

    /** One log line per lying end every 5 seconds, debug screen or not. */
    private static void log(CableEndEntity entity, boolean cord, double yaw, Vec3 at, Vector3d back, @Nullable Vec3 from,
                            @Nullable CableReelBlockEntity be) {
        long now = System.currentTimeMillis();
        Long last = LOGGED.get(entity.getId());
        if (last != null && now - last < 5000)
            return;
        if (LOGGED.size() > 256)
            LOGGED.clear();
        LOGGED.put(entity.getId(), now);
        Voltworks.LOGGER.info("[voltworks] cable end {}: cord={} at={} yaw={} back={} outlet={} wire={}", entity.getId(), cord, at,
                Math.toDegrees(yaw), back, from, be == null ? null : be.wireType());
    }

    /** F3 on: a green box on the entity origin (on the ground), a red one where the cable goes in, a yellow line to the reel outlet. */
    private static void debug(PoseStack pose, MultiBufferSource buffer, Vec3 at, Vector3d back, @Nullable Vec3 from) {
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        double r = 1 / 32.0;
        LevelRenderer.renderLineBox(pose, lines, new AABB(-r, 0, -r, r, 2 * r, r), 0, 1, 0, 1);
        LevelRenderer.renderLineBox(pose, lines, new AABB(back.x - r, back.y - r, back.z - r, back.x + r, back.y + r, back.z + r), 1, 0, 0, 1);
        if (from != null) {
            PoseStack.Pose last = pose.last();
            float dx = (float) (from.x - at.x), dy = (float) (from.y - at.y), dz = (float) (from.z - at.z);
            float len = Math.max(1.0E-4f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
            lines.addVertex(last, (float) back.x, (float) back.y, (float) back.z).setColor(1f, 1f, 0f, 1f).setNormal(last, dx / len, dy / len, dz / len);
            lines.addVertex(last, dx, dy, dz).setColor(1f, 1f, 0f, 1f).setNormal(last, dx / len, dy / len, dz / len);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(CableEndEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
