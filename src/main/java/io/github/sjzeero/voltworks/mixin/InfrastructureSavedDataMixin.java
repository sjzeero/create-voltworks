package io.github.sjzeero.voltworks.mixin;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import io.github.sjzeero.voltworks.cord.CordPair;
import io.github.sjzeero.voltworks.wallwire.WallCell;
import io.github.sjzeero.voltworks.wallwire.WallRoute;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import io.github.sjzeero.voltworks.wallwire.WallWireData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InfrastructureSavedData.class, remap = false)
public abstract class InfrastructureSavedDataMixin {
    @Shadow public ServerLevel level;

    @Shadow public abstract WireData removeConnection(InWorldNodeConnection connection);

    @Shadow public abstract WireData removeConnectionNoDrops(InWorldNodeConnection connection);

    @Shadow public abstract WireData getConnectionData(InWorldNodeConnection connection);

    @Inject(method = {"removeConnection", "removeConnectionNoDrops"}, at = @At("RETURN"), require = 0)
    private void voltworks$forgetWallRoute(InWorldNodeConnection connection, CallbackInfoReturnable<WireData> cir) {
        if(level != null)
            WallWireData.get(level).queueCheck(connection);
    }

    @Inject(method = "connect(Lcom/george_vi/electroenergetics/foundation/nodes/InWorldNode;Lcom/george_vi/electroenergetics/foundation/nodes/InWorldNode;Lcom/george_vi/electroenergetics/simulation/WireType;)J", at = @At("RETURN"))
    private void voltworks$wallRouteOnConnect(com.george_vi.electroenergetics.foundation.nodes.InWorldNode a, com.george_vi.electroenergetics.foundation.nodes.InWorldNode b, com.george_vi.electroenergetics.simulation.WireType type, CallbackInfoReturnable<Long> cir) {
        if(level != null)
            io.github.sjzeero.voltworks.wallwire.SchematicWires.onConnect(level, a, b);
    }

    /**
     * Same drops as a hanging wire, but spread along the faces the wire ran on. A copper cord is two connections made
     * from one spool, so its other conductor goes along without drops: the cord gives back one spool's worth of cord
     * wire, not two (e.g. when a socket with a cord is broken, both conductors would otherwise drop).
     */
    @Inject(method = "removeAndDropConnection", at = @At("HEAD"), cancellable = true, require = 0)
    private void voltworks$dropAlongRoute(InWorldNodeConnection connection, CallbackInfoReturnable<WireData> cir) {
        if(level == null)
            return;
        WireData own = getConnectionData(connection);
        if(own != null && CordPair.isCord(own.wireType())) {
            InWorldNodeConnection sibling = CordPair.sibling(connection);
            WireData other = getConnectionData(sibling);
            if(other != null && CordPair.isCord(other.wireType()))
                removeConnectionNoDrops(sibling);
        }
        WallRoute route = WallWireData.get(level).get(connection);
        if(route == null || route.cells.isEmpty())
            return;
        WireData data = removeConnection(connection);
        cir.setReturnValue(data);
        if(data == null)
            return;
        if(CEEConfigs.server().alternateWirePlacement.get()) {
            drop(route, 0.5f, new ItemStack(data.wireType().getSpooledItem()));
        } else {
            int n = CEEConfigs.server().wiresPerSpool.get();
            for(int i = 0; i < n; i++)
                drop(route, (i + 0.5f) / n, new ItemStack(data.wireType().getDrops()));
        }
    }

    private void drop(WallRoute route, float t, ItemStack stack) {
        WallCell cell = route.cells.get(Math.min(route.cells.size() - 1, (int) (t * route.cells.size())));
        Vec3 pos = WallSpace.toWorld(level, cell.point(0.2));
        Containers.dropItemStack(level, pos.x, pos.y, pos.z, stack);
    }
}
