package io.github.sjzeero.voltworks.wallwire.client;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.mixin_interfaces.ISchematicInfrastructureList;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sjzeero.voltworks.cord.CordPair;
import io.github.sjzeero.voltworks.wallwire.SchematicWires;
import io.github.sjzeero.voltworks.wallwire.WallRoute;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Draws the wall wires of a schematic hologram along their routes instead of Electro Energetics' straight wire. */
public final class SchematicWallRender {
    private static final double TOLERANCE = 0.75;
    private static final ThreadLocal<List<Vec3[]>> HIDDEN = new ThreadLocal<>();

    private SchematicWallRender() {
    }

    private static List<WallRoute> routes(SchematicLevel level) {
        List<WallRoute> all = SchematicWires.preview(level);
        if(all.isEmpty() || !(((Object) level) instanceof ISchematicInfrastructureList list))
            return List.of();
        Map<InWorldNodeConnection, WireData> wires = list.getWireConnections();
        List<WallRoute> out = new ArrayList<>();
        for(WallRoute route : all)
            if(wires.containsKey(route.connection))
                out.add(route);
        return out;
    }

    /** Called before the hologram is drawn: remember where the wall wires run so the straight ones can be skipped. */
    public static void begin(SchematicLevel level) {
        List<Vec3[]> hidden = new ArrayList<>();
        for(WallRoute route : routes(level))
            hidden.add(new Vec3[]{WallSpace.nodePos(level, route.connection.node1()), WallSpace.nodePos(level, route.connection.node2())});
        if(hidden.isEmpty())
            HIDDEN.remove();
        else
            HIDDEN.set(hidden);
    }

    public static void end() {
        HIDDEN.remove();
    }

    public static boolean hidden(Vec3 a, Vec3 b) {
        List<Vec3[]> hidden = HIDDEN.get();
        if(hidden == null)
            return false;
        for(Vec3[] h : hidden)
            if(a.distanceTo(h[0]) < TOLERANCE && b.distanceTo(h[1]) < TOLERANCE || a.distanceTo(h[1]) < TOLERANCE && b.distanceTo(h[0]) < TOLERANCE)
                return true;
        return false;
    }

    public static void draw(SchematicLevel level, PoseStack ms, SuperRenderTypeBuffer buffer) {
        List<WallRoute> routes = routes(level);
        if(routes.isEmpty())
            return;
        Map<InWorldNodeConnection, WireData> wires = ((ISchematicInfrastructureList) (Object) level).getWireConnections();
        for(WallRoute route : routes) {
            WireData data = wires.get(route.connection);
            if(data == null || CordPair.isCord(data.wireType()) && CordPair.isReturn(route.connection))
                continue;
            WallWireRenderer.drawSchematic(level, ms.last(), buffer, data.wireType(), route.color, route);
        }
    }
}
