package io.github.sjzeero.voltworks.mixin.client;

import com.george_vi.electroenergetics.client.ElectricPropertiesOverlay;
import com.george_vi.electroenergetics.content.clamp_meter.ClampMeterItem;
import com.george_vi.electroenergetics.content.linemans_stick.LinemansStickItem;
import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionHandler;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import io.github.sjzeero.voltworks.wallwire.client.ClientWallRoutes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {ClampMeterItem.class, LinemansStickItem.class}, remap = false)
public class WireMeterMixin {
    @Inject(method = "setMetering", at = @At("HEAD"), cancellable = true)
    private void voltworks$wallWireCurrent(float amperage, CallbackInfo ci) {
        NodeConnectionPoint point = WireInteractionHandler.targetedPoint;
        if(point != null && ClientWallRoutes.isWall(new InWorldNodeConnection(point.node1(), point.node2()))) {
            ElectricPropertiesOverlay.INSTANCE.setAmmeter(Math.abs(amperage));
            ci.cancel();
        }
    }
}
