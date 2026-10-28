package io.github.sjzeero.voltworks.wallwire.client;

import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import io.github.sjzeero.voltworks.cord.CordPair;
import io.github.sjzeero.voltworks.redstone.client.ClientRedstoneWires;
import io.github.sjzeero.voltworks.redstone.client.RedstoneCableClient;
import io.github.sjzeero.voltworks.reel.client.ReelClient;
import io.github.sjzeero.voltworks.registry.VoltworksWireTypes;
import io.github.sjzeero.voltworks.wallwire.ContraptionWireView;
import io.github.sjzeero.voltworks.wallwire.ContraptionWires;
import io.github.sjzeero.voltworks.wallwire.WallCell;
import io.github.sjzeero.voltworks.wallwire.WallRoute;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.createmod.catnip.data.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class WallWireRenderer {
    private static final double RENDER_DISTANCE = 128;
    private static final double PX = 1 / 16.0;
    private static final double TEX_PERIOD = 0.5;
    private static final Map<BakedModel, Tex> TEXTURES = new IdentityHashMap<>();

    private WallWireRenderer() {
    }

    private record Tex(float[] u, float[] v) {
        float u(double t, double s) {
            return (float) ((u[0] * (1 - s) + u[1] * s) * (1 - t) + (u[2] * (1 - s) + u[3] * s) * t);
        }

        float v(double t, double s) {
            return (float) ((v[0] * (1 - s) + v[1] * s) * (1 - t) + (v[2] * (1 - s) + v[3] * s) * t);
        }
    }

    public static int widthPixels(WireType type) {
        if(type == VoltworksWireTypes.COPPER_CORD.get())
            return 2;
        return isHeavy(type) ? 3 : 1;
    }

    public static int heightPixels(WireType type) {
        return isHeavy(type) ? 3 : 1;
    }

    private static boolean isHeavy(WireType type) {
        if(type == CEEWireTypes.HEAVILY_INSULATED.get())
            return true;
        for(Supplier<WireType> colored : CEEWireTypes.COLORED_HEAVILY_INSULATED_WIRES)
            if(colored.get() == type)
                return true;
        return false;
    }

    private static WireType look(WireType type, @Nullable DyeColor color) {
        return color != null && type instanceof WireType.Dyeable dyeable ? dyeable.getDyed(color) : type;
    }

    static void render(RenderLevelStageEvent event) {
        if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES)
            return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if(level == null || mc.player == null)
            return;
        boolean preview = WallWireClient.start != null && !WallWireClient.committed.isEmpty();
        boolean hover = WallWireClient.hoverSocket != null;
        boolean redPreview = !RedstoneCableClient.committed.isEmpty();
        Map<InWorldNodeConnection, WireData> wires = new HashMap<>();
        for(Pair<InWorldNodeConnection, WireData> w : WireRenderer.getAllConnections()) {
            wires.put(w.getFirst(), w.getSecond());
        }
        if(ClientWallRoutes.all().isEmpty() && !preview && !hover && !redPreview && ClientRedstoneWires.all().isEmpty())
            return;

        PoseStack pose = event.getPoseStack();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        Set<RenderType> used = new HashSet<>();

        for(ClientWallRoutes.Entry entry : ClientWallRoutes.all()) {
            WireData data = wires.get(entry.route().connection);
            if(data == null)
                continue;
            Pose3dc space = WallSpace.renderPose(level, entry.route().cells.getFirst().pos(), partial);
            AABB bounds = WallSpace.toWorld(space, entry.bounds());
            if(!event.getFrustum().isVisible(bounds))
                continue;
            if(bounds.getCenter().distanceTo(cam) > RENDER_DISTANCE + bounds.getSize())
                continue;
            InWorldNodeConnection c = entry.route().connection;
            if(CordPair.isCord(data.wireType()) && CordPair.isReturn(c))
                continue;
            new Drawer(level, pose.last(), buffer, used, data.wireType(), entry.route().color, cam, space)
                    .route(entry.route().cells, entry.segments(), c.node1(), c.node2());
        }

        WireType redType = CEEWireTypes.STANDARD.get();
        for(ClientRedstoneWires.Entry entry : ClientRedstoneWires.all()) {
            Pose3dc space = WallSpace.renderPose(level, entry.cells().getFirst().pos(), partial);
            AABB bounds = WallSpace.toWorld(space, entry.bounds());
            if(!event.getFrustum().isVisible(bounds) || bounds.getCenter().distanceTo(cam) > RENDER_DISTANCE + bounds.getSize())
                continue;
            new Drawer(level, pose.last(), buffer, used, redType, DyeColor.RED, cam, space).coil()
                    .route(entry.cells(), entry.segments(), (Vec3) null, (Vec3) null);
        }
        if(redPreview) {
            List<WallCell> redCells = List.copyOf(RedstoneCableClient.committed);
            Pose3dc redSpace = WallSpace.renderPose(level, redCells.getFirst().pos(), partial);
            new Drawer(level, pose.last(), buffer, used, redType, DyeColor.RED, cam, redSpace).coil()
                    .route(redCells, WallRoute.segments(redCells), (Vec3) null, (Vec3) null);
        }

        if(preview) {
            WireType type = WallWireClient.heldWireType(mc.player);
            if(type != null) {
                List<WallCell> cells = List.copyOf(WallWireClient.committed);
                Pose3dc space = WallSpace.renderPose(level, cells.getFirst().pos(), partial);
                new Drawer(level, pose.last(), buffer, used, type, WallWireClient.offhandDye(mc.player), cam, space)
                        .route(cells, WallRoute.segments(cells), WallWireClient.start, null);
            }
        }

        if(hover) {
            WireType type = WallWireClient.heldWireType(mc.player);
            if(type != null && CordPair.isCord(type)) {
                Vec3 towards = WallWireClient.start != null && !WallWireClient.start.sourcePos().equals(WallWireClient.hoverSocket.sourcePos())
                        ? WallSpace.toWorld(WallSpace.renderPose(level, WallWireClient.start.sourcePos(), partial), WallWireClient.start.sourcePos().getCenter())
                        : mc.player.getEyePosition(partial);
                ReelClient.drawPlugPreview(level, pose, cam, partial, WallWireClient.hoverSocket.sourcePos(), towards);
            }
        }

        for(Entity e : level.entitiesForRendering()) {
            if(!(e instanceof AbstractContraptionEntity ace) || !(ace.getContraption() instanceof ContraptionWireView view))
                continue;
            List<ContraptionWires.Drawn> drawn = view.voltworks$drawn();
            if(drawn == null || drawn.isEmpty())
                continue;
            double ex = Mth.lerp(partial, e.xOld, e.getX()), ey = Mth.lerp(partial, e.yOld, e.getY()), ez = Mth.lerp(partial, e.zOld, e.getZ());
            if(Math.sqrt((ex - cam.x) * (ex - cam.x) + (ey - cam.y) * (ey - cam.y) + (ez - cam.z) * (ez - cam.z)) > RENDER_DISTANCE)
                continue;
            try {
                PoseStack local = new PoseStack();
                ace.applyLocalTransforms(local, partial);
                Matrix4f m = new Matrix4f(local.last().pose());
                BlockPos eb = BlockPos.containing(ex, ey, ez);
                Pose3dc space = WallSpace.renderPose(level, eb, partial);
                int light = LevelRenderer.getLightColor(level, eb);
                UnaryOperator<Vec3> point = p -> {
                    Vector3f v = m.transformPosition((float) p.x, (float) p.y, (float) p.z, new Vector3f());
                    return WallSpace.toWorld(space, new Vec3(ex + v.x, ey + v.y, ez + v.z));
                };
                UnaryOperator<Vec3> normal = n -> {
                    Vector3f v = m.transformDirection((float) n.x, (float) n.y, (float) n.z, new Vector3f());
                    return WallSpace.rotate(space, new Vec3(v.x, v.y, v.z));
                };
                for(ContraptionWires.Drawn w : drawn)
                    new Drawer(level, pose.last(), buffer, used, w.type(), w.color(), cam, space, point, normal, light)
                            .route(w.cells(), w.segments(), w.p1(), w.p2());
            } catch(RuntimeException ex2) {
            }
        }

        for(RenderType type : used)
            buffer.endBatch(type);
    }

    public static void drawSchematic(Level level, PoseStack.Pose pose, MultiBufferSource buffer, WireType type, @Nullable DyeColor color, WallRoute route) {
        InWorldNodeConnection c = route.connection;
        new Drawer(level, pose, buffer, new HashSet<>(), type, color, Vec3.ZERO, null, p -> p, n -> n, 15728880)
                .route(route.cells, route.surfaceSegments(), c.node1(), c.node2());
    }

    private static Tex coilTex() {
        TextureAtlasSprite s = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(ResourceLocation.fromNamespaceAndPath("voltworks", "block/redstone_coil"));
        float u0 = s.getU0(), u1 = s.getU(2 / 16f), v0 = s.getV0(), v1 = s.getV(8 / 16f);
        return new Tex(new float[]{u0, u1, u0, u1}, new float[]{v0, v0, v1, v1});
    }

    private static Tex texture(WireType type) {
        BakedModel model = type.getModel().get();
        if(model == null)
            return extract(null);
        return TEXTURES.computeIfAbsent(model, WallWireRenderer::extract);
    }

    private static Tex extract(BakedModel model) {
        BakedQuad best = null;
        double bestScore = -1;
        if(model != null) {
            for(BakedQuad q : model.getQuads(null, null, RandomSource.create(42), ModelData.EMPTY, null)) {
                double score = zExtent(q) + (q.getDirection() == Direction.UP ? 0.01 : 0);
                if(score > bestScore) {
                    bestScore = score;
                    best = q;
                }
            }
        }
        if(best == null || bestScore < 0.05) {
            TextureAtlasSprite s = model != null ? model.getParticleIcon(ModelData.EMPTY) : null;
            if(s == null)
                return new Tex(new float[]{0, 1, 0, 1}, new float[]{0, 0, 1, 1});
            float u0 = s.getU0(), u1 = s.getU(2 / 16f), v0 = s.getV0(), v1 = s.getV(8 / 16f);
            return new Tex(new float[]{u0, u1, u0, u1}, new float[]{v0, v0, v1, v1});
        }
        int[] data = best.getVertices();
        int stride = data.length / 4;
        float[][] p = new float[4][];
        float[][] uv = new float[4][];
        for(int i = 0; i < 4; i++) {
            int o = i * stride;
            p[i] = new float[]{Float.intBitsToFloat(data[o]), Float.intBitsToFloat(data[o + 1]), Float.intBitsToFloat(data[o + 2])};
            uv[i] = new float[]{Float.intBitsToFloat(data[o + 4]), Float.intBitsToFloat(data[o + 5])};
        }
        float zMin = Math.min(Math.min(p[0][2], p[1][2]), Math.min(p[2][2], p[3][2]));
        int a = -1, b = -1, c = -1, d = -1;
        for(int i = 0; i < 4; i++) {
            if(Math.abs(p[i][2] - zMin) < 1e-3) {
                if(a < 0) a = i;
                else b = i;
            }
        }
        if(a < 0 || b < 0)
            return new Tex(new float[]{uv[0][0], uv[1][0], uv[3][0], uv[2][0]}, new float[]{uv[0][1], uv[1][1], uv[3][1], uv[2][1]});
        for(int i = 0; i < 4; i++) {
            if(i == a || i == b)
                continue;
            if(c < 0 || planar(p[i], p[a]) < planar(p[c], p[a])) {
                d = c;
                c = i;
            } else
                d = i;
        }
        return new Tex(new float[]{uv[a][0], uv[b][0], uv[c][0], uv[d][0]}, new float[]{uv[a][1], uv[b][1], uv[c][1], uv[d][1]});
    }

    private static double zExtent(BakedQuad q) {
        int[] data = q.getVertices();
        int stride = data.length / 4;
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for(int i = 0; i < 4; i++) {
            float z = Float.intBitsToFloat(data[i * stride + 2]);
            min = Math.min(min, z);
            max = Math.max(max, z);
        }
        return max - min;
    }

    private static double planar(float[] p, float[] q) {
        return (p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]);
    }

    private static final class Drawer {
        private final WireType type;
        final Level level;
        final PoseStack.Pose pose;
        final VertexConsumer vb;
        Tex tex;
        final Vec3 cam;
        final double w, h;
        final @Nullable Pose3dc space;
        final UnaryOperator<Vec3> mapPoint, mapNormal;
        final int fixedLight;

        Drawer(Level level, PoseStack.Pose pose, MultiBufferSource buffer, Set<RenderType> used, WireType type, @Nullable DyeColor color, Vec3 cam, @Nullable Pose3dc space) {
            this(level, pose, buffer, used, type, color, cam, space, p -> WallSpace.toWorld(space, p), n -> space == null ? n : space.transformNormal(n), -1);
        }

        Drawer(Level level, PoseStack.Pose pose, MultiBufferSource buffer, Set<RenderType> used, WireType type, @Nullable DyeColor color, Vec3 cam, @Nullable Pose3dc space,
               UnaryOperator<Vec3> mapPoint, UnaryOperator<Vec3> mapNormal, int fixedLight) {
            this.level = level;
            this.space = space;
            this.mapPoint = mapPoint;
            this.mapNormal = mapNormal;
            this.fixedLight = fixedLight;
            this.pose = pose;
            WireType look = look(type, color);
            RenderType rt = look.renderType();
            used.add(rt);
            this.vb = buffer.getBuffer(rt);
            this.tex = texture(look);
            this.cam = cam;
            this.type = type;
            this.w = widthPixels(type) * PX;
            this.h = heightPixels(type) * PX;
        }

        Drawer coil() {
            this.tex = coilTex();
            return this;
        }

        void route(List<WallCell> cells, List<WallRoute.Segment> segments, InWorldNode node1, InWorldNode node2) {
            if(cells.isEmpty())
                return;
            route(cells, segments, (Vec3) null, (Vec3) null);
            if(node1 != null)
                end(node1, cells.getFirst(), segments.isEmpty() ? null : segments.getFirst());
            if(node2 != null)
                end(node2, cells.getLast(), segments.isEmpty() ? null : segments.getLast());
        }

        int light(BlockPos pos) {
            return fixedLight >= 0 ? fixedLight : LevelRenderer.getLightColor(level, pos);
        }

        void route(List<WallCell> cells, List<WallRoute.Segment> segments, Vec3 node1, Vec3 node2) {
            if(cells.isEmpty())
                return;
            if(segments.isEmpty())
                pad(cells.getFirst());
            else
                for(int i = 0; i < segments.size(); i++)
                    segment(segments, i);
            if(node1 != null)
                lead(node1, cells.getFirst(), segments.isEmpty() ? null : segments.getFirst());
            if(node2 != null)
                lead(node2, cells.getLast(), segments.isEmpty() ? null : segments.getLast());
        }

        private void end(InWorldNode node, WallCell cell, WallRoute.Segment seg) {
            InWorldNode[] pair = CordPair.isCord(type) ? CordPair.ends(level, node) : null;
            Vec3 a = pair == null ? null : WallSpace.nodePos(level, pair[0]), b = pair == null ? null : WallSpace.nodePos(level, pair[1]);
            if(a == null || b == null) {
                lead(WallSpace.nodePos(level, node), cell, seg);
                return;
            }
            lead(a.add(b).scale(0.5), cell, seg);
        }

        private static Vec3 step(Direction d) {
            return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
        }

        private void segment(List<WallRoute.Segment> segs, int i) {
            WallRoute.Segment seg = segs.get(i);
            if(seg.air()) {
                air(seg);
                return;
            }
            Vec3 dir = seg.b().subtract(seg.a());
            double len = dir.length();
            if(len < 1e-6)
                return;
            Vec3 u = dir.scale(1 / len);
            Vec3 n = step(seg.normal());
            double lift = WallCell.LIFT;
            Vec3 a0 = seg.a().subtract(n.scale(lift));
            double from = 0, to = len;
            boolean capStart = true, capEnd = true;

            if(i == 0 || segs.get(i - 1).air()) {
                from -= w / 2;
            } else {
                WallRoute.Segment prev = segs.get(i - 1);
                Vec3 pu = prev.b().subtract(prev.a()).normalize();
                if(prev.normal() == seg.normal()) {
                    if(pu.dot(u) > 0.99)
                        capStart = false;
                    else {
                        from += w / 2;
                        capStart = false;
                    }
                } else if(pu.dot(n) < 0) {
                    from += h - lift;
                    capStart = false;
                } else {
                    from += lift;
                    capStart = false;
                }
            }
            if(i == segs.size() - 1 || segs.get(i + 1).air()) {
                to += w / 2;
            } else {
                WallRoute.Segment next = segs.get(i + 1);
                Vec3 nu = next.b().subtract(next.a()).normalize();
                Vec3 nn = step(next.normal());
                if(next.normal() == seg.normal()) {
                    if(nu.dot(u) > 0.99)
                        capEnd = false;
                    else
                        to += w / 2;
                } else if(u.dot(nn) < 0) {
                    to += lift;
                    capEnd = false;
                } else {
                    to += h - lift;
                }
            }
            if(to - from < 1e-5)
                return;
            Vec3 s = n.cross(u);
            Vec3 origin = a0.add(u.scale(from)).subtract(s.scale(w / 2));
            int light = light(seg.lightPos());
            box(origin, u, s, n, to - from, w, h, capStart, capEnd, false, light);
        }

        private void air(WallRoute.Segment seg) {
            Vec3 u = step(seg.normal());
            double lift = WallCell.LIFT;
            Vec3 start = seg.a().add(u.scale(h - lift));
            double len = seg.b().subtract(u.scale(h - lift)).subtract(start).dot(u);
            if(len < 1e-5)
                return;
            Vec3 up = Math.abs(u.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 s = u.cross(up);
            Vec3 origin = start.subtract(s.scale(w / 2)).subtract(up.scale(h / 2));
            box(origin, u, s, up, len, w, h, false, false, true, light(seg.lightPos()));
        }

        private void pad(WallCell cell) {
            Vec3 n = step(cell.face());
            Vec3 u = Math.abs(n.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 s = n.cross(u);
            Vec3 c = cell.point(0);
            Vec3 origin = c.subtract(u.scale(w / 2)).subtract(s.scale(w / 2));
            box(origin, u, s, n, w, w, h, true, true, false, light(cell.front()));
        }

        private void lead(Vec3 node, WallCell cell, WallRoute.Segment seg) {
            if(node == null)
                return;
            Vec3 n = step(cell.face());
            Vec3 end = cell.point(0).add(n.scale(h / 2));
            Vec3 d = end.subtract(node);
            double len = d.length();
            if(len < h / 2)
                return;
            Vec3 u = d.scale(1 / len);
            Vec3 side = n.cross(u);
            if(side.lengthSqr() < 1e-6) {
                Vec3 along = seg != null ? seg.b().subtract(seg.a()).normalize() : (Math.abs(n.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0));
                side = n.cross(along);
            }
            side = side.normalize();
            Vec3 up = u.cross(side).normalize();
            Vec3 origin = node.subtract(side.scale(w / 2)).subtract(up.scale(h / 2));
            int light = WireRenderer.maxLightLevel(light(BlockPos.containing(node)),
                    light(cell.front()));
            box(origin, u, side, up, Math.max(h / 2, len - h / 2), w, h, true, true, true, light);
        }

        private void box(Vec3 o, Vec3 u, Vec3 s, Vec3 n, double len, double w, double h,
                         boolean capStart, boolean capEnd, boolean bottom, int light) {
            double pos = 0;
            while(pos < len - 1e-6) {
                double next = Math.min(len, (Math.floor(pos / TEX_PERIOD + 1e-6) + 1) * TEX_PERIOD);
                double t0 = pos / TEX_PERIOD - Math.floor(pos / TEX_PERIOD + 1e-6);
                double t1 = t0 + (next - pos) / TEX_PERIOD;
                Vec3 a = o.add(u.scale(pos)), b = o.add(u.scale(next));
                Vec3 sw = s.scale(w), nh = n.scale(h);
                double sideS = Math.min(1, h / w);
                face(a.add(nh), b.add(nh), b.add(nh).add(sw), a.add(nh).add(sw), n, t0, t1, 0, 1, light);
                if(bottom)
                    face(a, b, b.add(sw), a.add(sw), n.scale(-1), t0, t1, 0, 1, light);
                face(a, b, b.add(nh), a.add(nh), s.scale(-1), t0, t1, 0, sideS, light);
                face(a.add(sw), b.add(sw), b.add(sw).add(nh), a.add(sw).add(nh), s, t0, t1, 0, sideS, light);
                if(capStart && pos == 0)
                    cap(a, sw, nh, u.scale(-1), light);
                if(capEnd && next >= len - 1e-6)
                    cap(b, sw, nh, u, light);
                pos = next;
            }
        }

        private void cap(Vec3 p, Vec3 sw, Vec3 nh, Vec3 normal, int light) {
            double t = Math.min(1, nh.length() / TEX_PERIOD);
            face(p, p.add(nh), p.add(nh).add(sw), p.add(sw), normal, 0, t, 0, 1, light);
        }

        private void face(Vec3 c0, Vec3 c1, Vec3 c2, Vec3 c3, Vec3 normal, double t0, double t1, double s0, double s1, int light) {
            Vec3[] c = {c0, c1, c2, c3};
            double[][] ts = {{t0, s0}, {t1, s0}, {t1, s1}, {t0, s1}};
            boolean flip = c1.subtract(c0).cross(c3.subtract(c0)).dot(normal) < 0;
            float shade = shade(normal);
            int col = (int) (255 * shade);
            Vec3 worldNormal = mapNormal.apply(normal);
            float nx = (float) worldNormal.x, ny = (float) worldNormal.y, nz = (float) worldNormal.z;
            for(int k = 0; k < 4; k++) {
                int i = flip ? (4 - k) % 4 : k;
                Vec3 p = mapPoint.apply(c[i]).subtract(cam);
                vb.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                        .setColor(col, col, col, 255)
                        .setUv(tex.u(ts[i][0], ts[i][1]), tex.v(ts[i][0], ts[i][1]))
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, nx, ny, nz);
            }
        }

        private static float shade(Vec3 n) {
            double up = n.y > 0 ? 1.0 : 0.5;
            return (float) (n.x * n.x * 0.6 + n.z * n.z * 0.8 + n.y * n.y * up);
        }
    }
}
