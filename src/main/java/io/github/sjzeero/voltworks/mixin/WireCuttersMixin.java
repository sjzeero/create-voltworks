package io.github.sjzeero.voltworks.mixin;

import com.george_vi.electroenergetics.content.wire_spool.WireCuttersWireInteractionBehaviour;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.simibubi.create.AllSoundEvents;
import io.github.sjzeero.voltworks.cord.CordPair;
import io.github.sjzeero.voltworks.cord.LooseCords;
import io.github.sjzeero.voltworks.reel.CableReels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WireCuttersWireInteractionBehaviour.class, remap = false)
public class WireCuttersMixin {
    @Inject(method = "interactWire", at = @At("HEAD"), cancellable = true)
    private void voltworks$cutCord(NodeConnectionPoint point, Level level, Player player, ItemStack stack, CallbackInfo ci) {
        if(!(player.level() instanceof ServerLevel sl))
            return;
        InWorldNodeConnection c = new InWorldNodeConnection(point.node1(), point.node2());
        InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
        WireData data = sd.getConnectionData(c);
        BlockPos at = BlockPos.containing(io.github.sjzeero.voltworks.wallwire.WallSpace.toWorld(sl, point.node1().sourcePos().getCenter()));
        if(data == null) {
            ci.cancel();
            return;
        }
        if(CableReels.windBack(sl, c)) {
            AllSoundEvents.WRENCH_REMOVE.playOnServer(sl, at);
            ci.cancel();
            return;
        }
        if(CordPair.isCord(data.wireType())) {
            LooseCords.get(sl).cutLaid(c);
            AllSoundEvents.WRENCH_REMOVE.playOnServer(sl, at);
            ci.cancel();
        }
    }
}
