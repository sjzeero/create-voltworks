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
import io.github.sjzeero.voltworks.registry.VoltworksItems;
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

import java.util.ArrayList;
import java.util.List;

/**
 * A lying cable end: the Cable End item laid flat on the ground, and the cable from the reel outlet to it drawn with
 * Electro Energetics' wire renderer and the reel's wire type, like the end carried in the hand. A cable lying on the
 * floor does not hang below the floor: no point of it goes lower than its lower end.
 */
public class CableEndRenderer extends EntityRenderer<CableEndEntity> {
    private final ItemRenderer items;
    private ItemStack plug;

    public CableEndRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        items = ctx.getItemRenderer();
        shadowRadius = 0.1f;
    }

    @Override
    public boolean shouldRender(CableEndEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true; // the cable reaches far outside the end's own little box
    }

    @Override
    public void render(CableEndEntity entity, float yaw, float pt, PoseStack pose, MultiBufferSource buffer, int light) {
        Level level = entity.level();
        Vec3 at = entity.getPosition(pt);

        if (plug == null)
            plug = new ItemStack(VoltworksItems.REEL_PLUG.get());
        pose.pushPose();
        pose.translate(0, 0.03, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(0.5f, 0.5f, 0.5f);
        items.renderStatic(plug, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffer, level, entity.getId());
        pose.popPose();

        if (level.isLoaded(entity.reelPos()) && level.getBlockEntity(entity.reelPos()) instanceof CableReelBlockEntity be) {
            WireType type = be.wireType();
            if (type != null) {
                CableReelBlock block = be.block();
                Vec3 local = block.localNode(be.getBlockState(), block.outletId());
                Vec3 from = WallSpace.toWorld(WallSpace.renderPose(level, be.getBlockPos(), pt), Vec3.atLowerCornerOf(be.getBlockPos()).add(local));
                Vec3 to = at.add(0, 0.05, 0);
                double distance = from.distanceTo(to);
                if (distance > 0.05) {
                    WireData look = WireData.ofLength(type, ReelMath.followLength(distance, Math.max(be.stored(), distance)));
                    float sag = look.getSag(distance);
                    List<Vec3> points = type.shouldScaleLast()
                            ? QuadraticWireHelper.cablePoints(from, to, sag)
                            : QuadraticWireHelper.cablePointsRaw(from, to, sag);
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
        }
        super.render(entity, yaw, pt, pose, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(CableEndEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
