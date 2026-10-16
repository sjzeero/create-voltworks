package io.github.sjzeero.voltworks.mixin;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireSimulationState;
import io.github.sjzeero.voltworks.wallwire.WallCell;
import io.github.sjzeero.voltworks.wallwire.WallRoute;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import io.github.sjzeero.voltworks.wallwire.WallWireData;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

/** Wall wires get their points from the route instead of a sagging curve, so sections, contacts and shocks follow the faces. */
@Mixin(value = WireSimulationState.class, remap = false)
public abstract class WireSimulationStateMixin {
    @Shadow @Final public InfrastructureSavedData sd;
    @Shadow @Final public Level level;
    @Shadow @Final private Map<InWorldNodeConnection, ConnectionEntry> connections;
    @Shadow @Final private it.unimi.dsi.fastutil.longs.Long2ObjectMap<Map<InWorldNodeConnection, ConnectionEntry>> connectionsBySection;
    @Shadow @Final private Map<InWorldNodeConnection, List<WireSimulationState.CutWireEntry>> cutsByWire;

    @Shadow public abstract void reloadLazyConnections();

    @Inject(method = "addConnection", at = @At("HEAD"), cancellable = true, require = 0)
    private void voltworks$addWallConnection(InWorldNodeConnection connection, WireData wireData, boolean load, CallbackInfo ci) {
        if(!(level instanceof ServerLevel serverLevel))
            return;
        WallRoute route = WallWireData.get(serverLevel).get(connection);
        if(route == null || route.cells.isEmpty())
            return;
        ci.cancel();
        Vec3 pos1 = sd.getNodePositionOrCenter(connection.node1());
        Vec3 pos2 = sd.getNodePositionOrCenter(connection.node2());
        List<Vec3> points = new ArrayList<>(route.samplePoints(pos1, pos2));
        points.replaceAll(p -> WallSpace.toWorld(level, p));
        if(wireData.length == 0)
            wireData.length = route.length;

        List<WireSimulationState.CutWireEntry> cuts = new ArrayList<>();
        cutsByWire.put(connection, cuts);
        ConnectionEntry entry = new ConnectionEntry(pos1, pos2, points, wireData, bounds(route, pos1, pos2).inflate(0.25), cuts);
        connections.put(connection, entry);
        SectionPos prev = null;
        for(Vec3 point : points) {
            SectionPos section = SectionPos.of(point);
            if(!Objects.equals(prev, section)) {
                connectionsBySection.computeIfAbsent(section.asLong(), s -> new HashMap<>()).put(connection, entry);
                prev = section;
            }
        }
        if(load)
            return;
        sd.wireCrossContactModule.onWireAdded(connection, entry);
        reloadLazyConnections();
    }

    @Unique
    private AABB bounds(WallRoute route, Vec3 pos1, Vec3 pos2) {
        AABB bb = new AABB(pos1, pos2);
        for(WallCell c : route.cells) {
            Vec3 p = WallSpace.toWorld(level, c.point());
            bb = bb.minmax(new AABB(p, p));
        }
        return bb;
    }
}
