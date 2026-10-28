package io.github.sjzeero.voltworks.mixin.client;

import com.george_vi.electroenergetics.client.WireVisual;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import io.github.sjzeero.voltworks.wallwire.client.ClientWallRoutes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.world.phys.Vec3;

@Mixin(value = WireVisual.class, remap = false)
public abstract class WireVisualMixin {
    @Shadow @Final private InWorldNodeConnection connection;
    @Shadow @Final private List<TransformedInstance> instances;
    @Shadow private TransformedInstance startInstance;
    @Shadow private TransformedInstance endInstance;
    @Shadow private Vec3 prevPos1;
    @Shadow @Final private com.george_vi.electroenergetics.simulation.infrastructure.WireData wireData;

    @Unique
    private boolean voltworks$hidden;

    @Inject(method = "recreateInstances", at = @At("HEAD"), cancellable = true, require = 0)
    private void voltworks$hideWallWire(CallbackInfo ci) {
        if(!ClientWallRoutes.isWall(connection)) {
            io.github.sjzeero.voltworks.reel.client.ReelClient.followLength(connection, wireData,
                    net.createmod.catnip.animation.AnimationTickHolder.getPartialTicks());
            if(voltworks$hidden) {
                voltworks$hidden = false;
                prevPos1 = null;
            }
            return;
        }
        ci.cancel();
        if(voltworks$hidden)
            return;
        voltworks$hidden = true;
        for(TransformedInstance instance : instances) {
            instance.setVisible(false);
            instance.setChanged();
        }
        if(startInstance != null) {
            startInstance.setVisible(false);
            startInstance.setChanged();
        }
        if(endInstance != null) {
            endInstance.setVisible(false);
            endInstance.setChanged();
        }
    }
}
