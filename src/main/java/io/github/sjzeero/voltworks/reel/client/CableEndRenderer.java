package io.github.sjzeero.voltworks.reel.client;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.QuadraticWireHelper;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sjzeero.voltworks.reel.CableEndEntity;
import io.github.sjzeero.voltworks.reel.CableReelBlock;
import io.github.sjzeero.voltworks.reel.CableReelBlockEntity;
import io.github.sjzeero.voltworks.reel.ReelMath;
import io.github.sjzeero.voltworks.reel.ReelPlugItem;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
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
    private record Lying(Quaternionf base, double axis, double floorPx, Vec3 backPx) {}

    // cord_plug is modelled standing in a floor socket (blades down, cord to +X): on the floor it lies on its back,
    // blades up. cable_lug lies flat on y = 6, ring towards -Z, barrel back at z = 11.
    private static final Lying PLUG = new Lying(Axis.XP.rotationDegrees(180), Math.PI / 2, 10, new Vec3(13, 11.5, 8));
    private static final Lying LUG = new Lying(new Quaternionf(), 0, 6, new Vec3(8, 7, 11));

    @Override
    public void render(CableEndEntity entity, float entityYaw, float pt, PoseStack pose, MultiBufferSource buffer, int light) {
        Level level = entity.level();
        Vec3 at = entity.getPosition(pt);
        CableReelBlockEntity be = level.isLoaded(entity.reelPos()) && level.getBlockEntity(entity.reelPos()) instanceof CableReelBlockEntity r ? r : null;
        boolean cord = be != null && be.block().cord;

        Vec3 from = null;
        if (be != null) {
            CableReelBlock block = be.block();
            Vec3 local = block.localNode(be.getBlockState(), block.outletId());
            from = WallSpace.toWorld(WallSpace.renderPose(level, be.getBlockPos(), pt), Vec3.atLowerCornerOf(be.getBlockPos()).add(local));
        }
        // lie along the cable: the back of the end towards the reel
        Lying lie = cord ? PLUG : LUG;
        double yaw = Math.toRadians(-entity.getYRot());
        if (from != null && Math.hypot(from.x - at.x, from.z - at.z) > 0.05)
            yaw = Math.atan2(from.x - at.x, from.z - at.z);
        yaw -= lie.axis();

        pose.pushPose();
        pose.mulPose(Axis.YP.rotation((float) yaw));
        pose.translate(0, (8 - lie.floorPx()) / 16, 0);
        pose.mulPose(lie.base());
        items.renderStatic(cord ? plug : lug, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffer, level, entity.getId());
        pose.popPose();

        WireType type = be == null ? null : be.wireType();
        if (type != null) {
            Vec3 back = lie.backPx();
            double bx = (back.x - 8) / 16, by = (back.y - lie.floorPx()) / 16, bz = (back.z - 8) / 16;
            double cos = Math.cos(yaw), sin = Math.sin(yaw);
            Vec3 to = at.add(bx * cos + bz * sin, by, -bx * sin + bz * cos);
            double distance = from.distanceTo(to);
            if (distance > 0.05) {
                WireData look = WireData.ofLength(type, ReelMath.followLength(distance, Math.max(be.stored(), distance)));
                float sag = look.getSag(distance);
                List<Vec3> points = type.shouldScaleLast()
                        ? QuadraticWireHelper.cablePoints(from, to, sag)
                        : QuadraticWireHelper.cablePointsRaw(from, to, sag);
                // a cable lying on the floor does not hang through it
                double floor = Math.min(from.y, to.y);
                List<Vec3> lying = new ArrayList<>(points.size());
                for (Vec3 p : points)
                    lying.add(p.y < floor ? new Vec3(p.x, floor, p.z) : p);
                pose.pushPose();
                pose.translate(-at.x, -at.y, -at.z); // entity space -> world space relative to the camera
                WireRenderer.forceRenderWire(lying, from, to, pose, buffer, type, level);
                pose.popPose();
            }
        }
        super.render(entity, entityYaw, pt, pose, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(CableEndEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
