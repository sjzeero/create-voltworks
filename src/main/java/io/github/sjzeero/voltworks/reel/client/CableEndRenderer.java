package io.github.sjzeero.voltworks.reel.client;

import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sjzeero.voltworks.reel.CableEndEntity;
import io.github.sjzeero.voltworks.reel.CablePath;
import io.github.sjzeero.voltworks.reel.CableReelBlock;
import io.github.sjzeero.voltworks.reel.CableReelBlockEntity;
import io.github.sjzeero.voltworks.reel.LyingEnd;
import io.github.sjzeero.voltworks.reel.ReelMath;
import io.github.sjzeero.voltworks.reel.ReelPlugItem;
import io.github.sjzeero.voltworks.registry.VoltworksWireTypes;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.cord.LooseCords;
import io.github.sjzeero.voltworks.cord.Outlets;
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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        return true;
    }

    @Override
    public void render(CableEndEntity entity, float entityYaw, float pt, PoseStack pose, MultiBufferSource buffer, int light) {
        if (entity.isCord()) {
            renderCord(entity, pt, pose, buffer, light);
            super.render(entity, entityYaw, pt, pose, buffer, light);
            return;
        }
        Level level = entity.level();
        Vec3 at = entity.getPosition(pt);
        CableReelBlockEntity be = level.isLoaded(entity.reelPos()) && level.getBlockEntity(entity.reelPos()) instanceof CableReelBlockEntity r ? r : null;
        boolean cord = be != null && be.block().cord;

        Vec3 from = null, fromOut = null;
        if (be != null) {
            CableReelBlock block = be.block();
            Vec3 local = block.localNode(be.getBlockState(), block.outletId());
            dev.ryanhcode.sable.companion.math.Pose3dc reelPose = WallSpace.renderPose(level, be.getBlockPos(), pt);
            from = WallSpace.toWorld(reelPose, Vec3.atLowerCornerOf(be.getBlockPos()).add(local));
            fromOut = WallSpace.rotate(reelPose, block.outletDir(be.getBlockState()));
        }
        LyingEnd.Lie lie = cord ? LyingEnd.PLUG : LyingEnd.LUG;
        double fallback = Math.toRadians(-entity.getYRot());
        double yaw = from == null ? fallback - lie.axis() : LyingEnd.yaw(lie, from.x - at.x, from.z - at.z, fallback);
        Matrix4d model = LyingEnd.pose(lie, yaw);

        ItemStack stack = cord ? plug : lug;
        BakedModel baked = items.getModel(stack, level, null, entity.getId());
        pose.pushPose();
        pose.mulPose(new Matrix4f(model));
        pose.translate(-0.5f, -0.5f, -0.5f);
        for (BakedModel pass : baked.getRenderPasses(stack, true))
            for (RenderType rt : pass.getRenderTypes(stack, true))
                items.renderModelLists(pass, stack, light, OverlayTexture.NO_OVERLAY, pose, buffer.getBuffer(rt));
        pose.popPose();

        Vector3d back = LyingEnd.toEntity(model, lie.backPx());
        if (Minecraft.getInstance().getDebugOverlay().showDebugScreen())
            debug(pose, buffer, at, back, from);
        log(entity, cord, yaw, at, back, from, be);
        WireType type = be == null ? null : be.wireType();
        if (type != null) {
            Vec3 to = at.add(back.x, back.y, back.z);
            double distance = from.distanceTo(to);
            if (distance > 0.05) {
                WireData look = WireData.ofLength(type, ReelMath.followLength(distance, Math.max(be.stored(), distance)));
                List<Vec3> points = ReelWire.points(from, fromOut, to, LyingEnd.direction(model, lie.backDir()), CablePath.RUN,
                        look.getSag(distance), Math.min(from.y, to.y), level, be.getBlockPos());
                pose.pushPose();
                pose.translate(-at.x, -at.y, -at.z);
                ReelWire.render(points, pose, buffer, type, level);
                pose.popPose();
            }
        }
        super.render(entity, entityYaw, pt, pose, buffer, light);
    }

    static Matrix4d cordModel(CableEndEntity entity, float pt) {
        Vec3 at = entity.getPosition(pt);
        Vec3 to = entity.otherAt(pt);
        LyingEnd.Lie lie = LyingEnd.PLUG;
        double fallback = Math.toRadians(-entity.getYRot());
        double yaw = to == null ? fallback - lie.axis() : LyingEnd.yaw(lie, to.x - at.x, to.z - at.z, fallback);
        return LyingEnd.pose(lie, yaw);
    }

    static Vec3[] cordBack(CableEndEntity entity, float pt) {
        Matrix4d model = cordModel(entity, pt);
        Vector3d back = LyingEnd.toEntity(model, LyingEnd.PLUG.backPx());
        Vector3d dir = LyingEnd.direction(model, LyingEnd.PLUG.backDir());
        return new Vec3[]{entity.getPosition(pt).add(back.x, back.y, back.z), new Vec3(dir.x, dir.y, dir.z)};
    }

    private void renderCord(CableEndEntity entity, float pt, PoseStack pose, MultiBufferSource buffer, int light) {
        Level level = entity.level();
        Vec3 at = entity.getPosition(pt);
        Matrix4d model = cordModel(entity, pt);
        BakedModel baked = items.getModel(plug, level, null, entity.getId());
        pose.pushPose();
        pose.mulPose(new Matrix4f(model));
        pose.translate(-0.5f, -0.5f, -0.5f);
        for (BakedModel pass : baked.getRenderPasses(plug, true))
            for (RenderType rt : pass.getRenderTypes(plug, true))
                items.renderModelLists(pass, plug, light, OverlayTexture.NO_OVERLAY, pose, buffer.getBuffer(rt));
        pose.popPose();

        Vector3d b = LyingEnd.toEntity(model, LyingEnd.PLUG.backPx());
        Vector3d d = LyingEnd.direction(model, LyingEnd.PLUG.backDir());
        Vec3 to = at.add(b.x, b.y, b.z);
        Vec3 from, fromOut;
        BlockPos skip;
        int supply = entity.otherSupply();
        if (supply >= 0) {
            BlockPos op = entity.otherPos();
            if (!level.isLoaded(op))
                return;
            from = LooseCords.plugWorld(level, op, supply);
            fromOut = WallSpace.rotate(WallSpace.renderPose(level, op, pt),
                    Outlets.cordDir(level.getBlockState(op), supply));
            skip = op;
        } else if (entity.otherHolder() != null || entity.end() != 0) {
            return;
        } else {
            CableEndEntity other = entity.otherEnd();
            if (other == null)
                return;
            Vec3[] ob = cordBack(other, pt);
            from = ob[0];
            fromOut = ob[1];
            skip = other.blockPosition();
        }
        WireType type = VoltworksWireTypes.COPPER_CORD.get();
        double distance = from.distanceTo(to);
        if (distance <= 0.05)
            return;
        WireData look = WireData.ofLength(type, ReelMath.followLength(distance, Math.max(entity.reach(), distance)));
        List<Vec3> points = ReelWire.points(from, fromOut, to, d, CablePath.RUN, look.getSag(distance), Math.min(from.y, to.y), level, skip);
        pose.pushPose();
        pose.translate(-at.x, -at.y, -at.z);
        ReelWire.render(points, pose, buffer, type, level);
        pose.popPose();
    }

    private static final Map<Integer, Long> LOGGED = new HashMap<>();

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
