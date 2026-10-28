package io.github.sjzeero.voltworks.mixin.client;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sjzeero.voltworks.reel.client.ReelClient;
import io.github.sjzeero.voltworks.wallwire.client.ClientWallRoutes;
import io.github.sjzeero.voltworks.wallwire.client.SchematicWallRender;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Pair;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = WireRenderer.class, remap = false)
public abstract class WireRendererMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/george_vi/electroenergetics/client/WireRenderer;getAllConnections()Ljava/util/List;"), require = 0)
    private static List<Pair<InWorldNodeConnection, WireData>> voltworks$skipWallWires() {
        return ClientWallRoutes.withoutWallWires(WireRenderer.getAllConnections());
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private static void voltworks$reelLengths(CallbackInfo ci) {
        ReelClient.followLengths(AnimationTickHolder.getPartialTicks());
    }

    @Inject(method = "forceRenderWire", at = @At("HEAD"), cancellable = true)
    private static void voltworks$skipSchematicWallWire(List<Vec3> points, Vec3 p1, Vec3 p2,
                                                       PoseStack pose, MultiBufferSource buffer,
                                                       WireType type, BlockAndTintGetter level,
                                                       CallbackInfo ci) {
        if(SchematicWallRender.hidden(p1, p2))
            ci.cancel();
    }
}
