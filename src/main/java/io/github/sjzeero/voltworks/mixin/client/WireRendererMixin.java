package io.github.sjzeero.voltworks.mixin.client;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import io.github.sjzeero.voltworks.wallwire.client.ClientWallRoutes;
import net.createmod.catnip.data.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import io.github.sjzeero.voltworks.Voltworks;

/** Wall wires are drawn by Voltworks, so Electro Energetics' immediate renderer skips them. */
@Mixin(value = WireRenderer.class, remap = false)
public abstract class WireRendererMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/george_vi/electroenergetics/client/WireRenderer;getAllConnections()Ljava/util/List;"), require = 0)
    private static List<Pair<InWorldNodeConnection, WireData>> voltworks$skipWallWires() {
        return ClientWallRoutes.withoutWallWires(WireRenderer.getAllConnections());
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "forceRenderWire", at = @At("HEAD"), cancellable = true)
    private static void voltworks$skipSchematicWallWire(List<net.minecraft.world.phys.Vec3> points, net.minecraft.world.phys.Vec3 p1, net.minecraft.world.phys.Vec3 p2,
                                                       com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffer,
                                                       com.george_vi.electroenergetics.simulation.WireType type, net.minecraft.world.level.BlockAndTintGetter level,
                                                       org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if(io.github.sjzeero.voltworks.wallwire.client.SchematicWallRender.hidden(p1, p2))
            ci.cancel();
    }
}
