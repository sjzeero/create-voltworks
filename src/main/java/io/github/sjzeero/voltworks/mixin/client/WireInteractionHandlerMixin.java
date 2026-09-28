package io.github.sjzeero.voltworks.mixin.client;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionHandler;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import io.github.sjzeero.voltworks.wallwire.client.ClientWallRoutes;
import io.github.sjzeero.voltworks.wallwire.client.WallWireClient;
import net.createmod.catnip.data.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Wall wires are picked along their faces instead of along an invisible hanging curve. */
@Mixin(value = WireInteractionHandler.class, remap = false)
public abstract class WireInteractionHandlerMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/george_vi/electroenergetics/client/WireRenderer;getAllConnections()Ljava/util/List;"))
    private static List<Pair<InWorldNodeConnection, WireData>> voltworks$skipWallWires() {
        return ClientWallRoutes.withoutWallWires(WireRenderer.getAllConnections());
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private static void voltworks$pickWallWires(CallbackInfo ci) {
        WallWireClient.pickWallWire();
    }
}
