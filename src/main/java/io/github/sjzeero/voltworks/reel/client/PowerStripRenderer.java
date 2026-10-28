package io.github.sjzeero.voltworks.reel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sjzeero.voltworks.cord.PowerStripEntity;
import io.github.sjzeero.voltworks.registry.VoltworksItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class PowerStripRenderer extends EntityRenderer<PowerStripEntity> {
    private final ItemRenderer items;
    private ItemStack look = ItemStack.EMPTY;

    public PowerStripRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        items = ctx.getItemRenderer();
        shadowRadius = 0.3f;
    }

    @Override
    public void render(PowerStripEntity entity, float entityYaw, float pt, PoseStack pose, MultiBufferSource buffer, int light) {
        if (look.isEmpty())
            look = new ItemStack(VoltworksItems.POWER_STRIP_ITEM.get());
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation((float) entity.yaw(pt)));
        pose.translate(0, 0.5, 0);
        items.renderStatic(look, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffer, entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, entityYaw, pt, pose, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PowerStripEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
