package io.github.sjzeero.voltworks.mixin.client;

import com.george_vi.electroenergetics.client.ElectricPropertiesOverlay;
import com.george_vi.electroenergetics.content.wire_spool.WireApplyingBehaviour;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import io.github.sjzeero.voltworks.cord.CordPair;
import io.github.sjzeero.voltworks.cord.SocketBlock;
import io.github.sjzeero.voltworks.cord.CordSpoolItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WireApplyingBehaviour.class, remap = false)
public class WireApplyingBehaviourMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void voltworks$cordOnlySockets(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if(mc.player == null || mc.level == null || !(mc.player.getMainHandItem().getItem() instanceof CordSpoolItem))
            return;
        if(mc.hitResult instanceof BlockHitResult hit && CordPair.ends(mc.level, hit.getBlockPos()) != null) {
            InWorldNode node = InWorldNode.getHitNode(hit, mc.level);
            if(node != null && (node.id() == SocketBlock.CORD_POSITIVE || node.id() == SocketBlock.CORD_NEGATIVE))
                return;
        }
        WireApplyingBehaviour.targetingDetachedNode = null;
        ElectricPropertiesOverlay.INSTANCE.invalidConnection = false;
        ElectricPropertiesOverlay.INSTANCE.connectionTooLong = false;
        ElectricPropertiesOverlay.INSTANCE.removeHoveredNode();
        ci.cancel();
    }
}
