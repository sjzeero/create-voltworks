package io.github.sjzeero.voltworks.mixin;

import com.george_vi.electroenergetics.content.wire_spool.WireCuttersWireInteractionBehaviour;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllSoundEvents;
import io.github.sjzeero.voltworks.cord.CordWires;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cutting a cord removes both strands and gives back one cord item. */
@Mixin(value = WireCuttersWireInteractionBehaviour.class, remap = false)
public abstract class WireCuttersWireInteractionBehaviourMixin {
    @Inject(method = "interactWire", at = @At("HEAD"), cancellable = true)
    private void voltworks$cutCord(NodeConnectionPoint point, Level level, Player player, ItemStack stack, CallbackInfo ci) {
        if(!(player.level() instanceof ServerLevel sl))
            return;
        InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
        InWorldNodeConnection connection = new InWorldNodeConnection(point.node1(), point.node2());
        if(!CordWires.isCord(sd.getConnectionData(connection)))
            return;
        sd.removeConnection(connection);
        AllSoundEvents.WRENCH_REMOVE.playOnServer(level, point.node1().sourcePos());
        CordWires.give(sl, point.node1().sourcePos().getCenter(), player);
        ci.cancel();
    }
}
