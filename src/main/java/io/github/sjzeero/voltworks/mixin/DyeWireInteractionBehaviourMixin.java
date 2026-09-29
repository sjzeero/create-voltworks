package io.github.sjzeero.voltworks.mixin;

import com.george_vi.electroenergetics.content.wire_spool.DyeWireInteractionBehaviour;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import io.github.sjzeero.voltworks.wallwire.WallWirePlacement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dyeing a wall wire only changes its colour, never the wire type. */
@Mixin(value = DyeWireInteractionBehaviour.class, remap = false)
public abstract class DyeWireInteractionBehaviourMixin {
    @Inject(method = "interactWire", at = @At("HEAD"), cancellable = true)
    private void voltworks$dyeWallWire(NodeConnectionPoint point, Level level, Player player, ItemStack stack, CallbackInfo ci) {
        if(player instanceof ServerPlayer sp && WallWirePlacement.dye(sp, point.connection(), stack))
            ci.cancel();
    }
}
