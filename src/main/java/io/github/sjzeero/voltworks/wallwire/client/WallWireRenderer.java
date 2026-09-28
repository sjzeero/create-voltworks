package io.github.sjzeero.voltworks.wallwire.client;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sjzeero.voltworks.wallwire.WallCell;
import io.github.sjzeero.voltworks.wallwire.WallRoute;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Draws wall wires as flat strips of the wire's own segment model laid on the faces. */
public final class WallWireRenderer {
    private static final double RENDER_DISTANCE = 128;

    private WallWireRenderer() {
    }

    static void render(RenderLevelStageEvent event) {
        if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES)
            return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if(level == null || mc.player == null)
            return;
        boolean preview = WallWireClient.start != null && !WallWireClient.committed.isEmpty();
        if(ClientWallRoutes.all().isEmpty() && !preview)
            return;

        Map<InWorldNodeConnection, WireData> wires = new HashMap<>();
        for(Pair<InWorldNodeConnection, WireData> w : WireRenderer.getAllConnections())
            wires.put(w.getFirst(), w.getSecond());

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        Set<RenderType> used = new HashSet<>();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        for(ClientWallRoutes.Entry entry : ClientWallRoutes.all()) {
            WireData data = wires.get(entry.route().connection);
            if(data == null || !event.getFrustum().isVisible(entry.bounds()))
                continue;
            if(entry.bounds().getCenter().distanceTo(cam) > RENDER_DISTANCE + entry.bounds().getSize())
                continue;
            WireType type = data.wireType();
            InWorldNodeConnection c = entry.route().connection;
            draw(level, pose, buffer, used, type, entry.route().cells, entry.segments(), c.node1(), c.node2());
        }

        if(preview) {
            WireType type = WallWireClient.heldWireType(mc.player);
            if(type != null) {
                List<WallCell> cells = List.copyOf(WallWireClient.committed);
                draw(level, pose, buffer, used, type, cells, WallRoute.segments(cells), WallWireClient.start, null);
            }
        }

        pose.popPose();
        for(RenderType type : used)
            buffer.endBatch(type);
    }

    private static void draw(ClientLevel level, PoseStack pose, MultiBufferSource buffer, Set<RenderType> used, WireType type,
                             List<WallCell> cells, List<WallRoute.Segment> segments, InWorldNode node1, InWorldNode node2) {
        RenderType renderType = type.renderType();
        used.add(renderType);
        VertexConsumer vb = buffer.getBuffer(renderType);
        for(WallRoute.Segment seg : segments)
            flat(level, pose, vb, type, seg);
        if(node1 != null)
            lead(level, pose, vb, type, node1.getPosition(level), cells.getFirst());
        if(node2 != null)
            lead(level, pose, vb, type, node2.getPosition(level), cells.getLast());
    }

    private static void flat(ClientLevel level, PoseStack pose, VertexConsumer vb, WireType type, WallRoute.Segment seg) {
        Vec3 dir = seg.b().subtract(seg.a());
        double len = dir.length();
        if(len < 1e-5)
            return;
        Vec3 u = dir.scale(1 / len);
        // overlap half the strip width at both ends so corners are filled
        Vec3 a = seg.a().subtract(u.scale(1 / 32.0));
        len += 1 / 16.0;
        Vector3f z = new Vector3f((float) u.x, (float) u.y, (float) u.z);
        Vector3f y = new Vector3f(seg.normal().getStepX(), seg.normal().getStepY(), seg.normal().getStepZ());
        Vector3f x = new Vector3f(y).cross(z);
        Quaternionf q = new Matrix3f(x, y, z).getNormalizedRotation(new Quaternionf());
        CachedBuffers.partial(type.getModel(), Blocks.ANDESITE.defaultBlockState())
                .translate(a.x, a.y, a.z)
                .rotate(q)
                .scale(1, 0.02f, (float) (len * 2))
                .light(LevelRenderer.getLightColor(level, seg.lightPos()))
                .renderInto(pose, vb);
    }

    private static void lead(ClientLevel level, PoseStack pose, VertexConsumer vb, WireType type, Vec3 nodePos, WallCell cell) {
        if(nodePos == null)
            return;
        Vec3 end = cell.point();
        Vec3 d = end.subtract(nodePos);
        double len = d.length();
        if(len < 1e-4)
            return;
        CachedBuffers.partial(type.getModel(), Blocks.ANDESITE.defaultBlockState())
                .translate(nodePos.x, nodePos.y, nodePos.z)
                .rotateY((float) Mth.atan2(d.x, d.z))
                .rotateX(-(float) Mth.atan2(d.y, Math.hypot(d.x, d.z)))
                .scaleZ((float) (len * 2) + 0.02f)
                .light(WireRenderer.maxLightLevel(LevelRenderer.getLightColor(level, BlockPos.containing(nodePos)),
                        LevelRenderer.getLightColor(level, cell.front())))
                .renderInto(pose, vb);
    }
}
