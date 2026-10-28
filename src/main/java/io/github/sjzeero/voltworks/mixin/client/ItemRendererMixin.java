package io.github.sjzeero.voltworks.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sjzeero.voltworks.reel.client.HeldEndTracker;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public class ItemRendererMixin {
    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0, shift = At.Shift.AFTER),
            require = 0)
    private void voltworks$heldEnd(ItemStack stack, ItemDisplayContext ctx, boolean leftHand, PoseStack pose, MultiBufferSource buffer,
                                   int light, int overlay, BakedModel model, CallbackInfo ci) {
        HeldEndTracker.capture(stack, ctx, pose);
    }
}
