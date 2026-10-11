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
            target = "Lcom/george_vi/electroenergetics/client/WireRenderer;getAllConnections()Ljava/util/List;"))
    private static List<Pair<InWorldNodeConnection, WireData>> voltworks$skipWallWires() {
        return ClientWallRoutes.withoutWallWires(WireRenderer.getAllConnections());
    }
}
