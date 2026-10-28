package io.github.sjzeero.voltworks.mixin.client;

import com.george_vi.electroenergetics.client.ElectricPropertiesOverlay;
import com.george_vi.electroenergetics.content.wire.interaction.OutlinesOnWireRenderer;
import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionHandler;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import io.github.sjzeero.voltworks.wallwire.client.ClientWallRoutes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clamp meter on a wire run along walls and floors: the current reads the same as on a hanging wire (the overlay), but
 * Electro Energetics' chasing dots would fly along a straight sagging line between the two ends, through the walls,
 * so they are left out there.
 */
@Mixin(value = OutlinesOnWireRenderer.class, remap = false)
public class OutlinesOnWireRendererMixin {
    @Inject(method = "renderCurrent", at = @At("HEAD"), cancellable = true)
    private static void voltworks$wallWireCurrent(float amperage, CallbackInfo ci) {
        NodeConnectionPoint point = WireInteractionHandler.targetedPoint;
        if(point != null && ClientWallRoutes.isWall(new InWorldNodeConnection(point.node1(), point.node2()))) {
            ElectricPropertiesOverlay.INSTANCE.setAmmeter(Math.abs(amperage));
            ci.cancel();
        }
    }
}
