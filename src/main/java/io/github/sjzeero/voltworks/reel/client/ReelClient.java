package io.github.sjzeero.voltworks.reel.client;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.sjzeero.voltworks.Voltworks;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import io.github.sjzeero.voltworks.cord.CordPair;
import io.github.sjzeero.voltworks.cord.Outlets;
import io.github.sjzeero.voltworks.cord.SocketBlock;
import io.github.sjzeero.voltworks.cord.SocketPlugs;
import io.github.sjzeero.voltworks.reel.CableEndEntity;
import io.github.sjzeero.voltworks.reel.CablePath;
import io.github.sjzeero.voltworks.reel.CableReelBlock;
import io.github.sjzeero.voltworks.reel.CableReelBlockEntity;
import io.github.sjzeero.voltworks.reel.ReelMath;
import io.github.sjzeero.voltworks.reel.ReelPlugItem;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import net.createmod.catnip.data.Pair;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ReelClient {
    private ReelClient() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ReelClient::render);
        HeldEndTracker.init();
    }

    private static @Nullable CableReelBlockEntity reelAt(Level level, InWorldNode node) {
        if (!level.isLoaded(node.sourcePos()))
            return null;
        BlockState state = level.getBlockState(node.sourcePos());
        if (!(state.getBlock() instanceof CableReelBlock reel) || !reel.isOutlet(node.id()))
            return null;
        return level.getBlockEntity(node.sourcePos()) instanceof CableReelBlockEntity be ? be : null;
    }

    public static void followLength(InWorldNodeConnection connection, WireData data, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || data == null)
            return;
        CableReelBlockEntity be = reelAt(level, connection.node1());
        if (be == null)
            be = reelAt(level, connection.node2());
        if (be == null)
            return;
        double total = be.stored() + be.pulled();
        if (total <= ReelMath.EPS)
            return;
        Vec3 p1 = connection.node1().getPosition(level, partialTick), p2 = connection.node2().getPosition(level, partialTick);
        if (p1 == null || p2 == null)
            return;
        data.length = ReelMath.followLength(p1.distanceTo(p2), total);
    }

    public static void followLengths(float partialTick) {
        for (Pair<InWorldNodeConnection, WireData> w : WireRenderer.getAllConnections())
            followLength(w.getFirst(), w.getSecond(), partialTick);
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES)
            return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null)
            return;
        renderPlugs(event, mc, level);
        renderHeld(event, mc, player, level);
    }

    private static final double PLUG_RANGE = 96;
    private static @Nullable ItemStack plugLook;

    private static void renderPlugs(RenderLevelStageEvent event, Minecraft mc, ClientLevel level) {
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        Set<Long> done = new HashSet<>();
        boolean any = false;
        for (Pair<InWorldNodeConnection, WireData> w : WireRenderer.getAllConnections()) {
            InWorldNodeConnection c = w.getFirst();
            boolean cord = CordPair.isCord(w.getSecond().wireType());
            if (!cord) {
                CableReelBlockEntity be = reelAt(level, c.node1());
                if (be == null)
                    be = reelAt(level, c.node2());
                if (be == null || !be.block().cord)
                    continue;
            }
            for (InWorldNode socket : new InWorldNode[]{c.node1(), c.node2()})
                any |= plugIn(mc, level, pose, buffer, cam, pt, socket.sourcePos(), socket.id(), done);
        }
        for (Entity e : level.entitiesForRendering())
            if (e instanceof CableEndEntity end && end.isCord() && end.otherSupply() >= 0)
                any |= plugIn(mc, level, pose, buffer, cam, pt, end.otherPos(), end.otherSupply(), done);
        for (AbstractClientPlayer p : level.players()) {
            LooseCordClient.Anchor a = LooseCordClient.anchor(p.getMainHandItem());
            if (a != null)
                any |= plugIn(mc, level, pose, buffer, cam, pt, a.pos(), a.supply(), done);
        }
        if (any)
            buffer.endBatch();
    }

    private static boolean plugIn(Minecraft mc, ClientLevel level, PoseStack pose, MultiBufferSource buffer, Vec3 cam, float pt,
                                  BlockPos sp, int id, Set<Long> done) {
        if (!level.isLoaded(sp))
            return false;
        BlockState st = level.getBlockState(sp);
        if (!Outlets.isContact(st, id))
            return false;
        if (st.getBlock() instanceof SocketBlock && st.getValue(SocketBlock.PLUG) == SocketBlock.Plug.NONE)
            return false;
        if (!done.add(sp.asLong() * 8 + (id & ~1)))
            return false;
        return drawPlugAt(mc, level, pose, buffer, cam, pt, sp, Outlets.plugPose(st, id & ~1));
    }

    public static boolean drawPlug(Minecraft mc, ClientLevel level, PoseStack pose, MultiBufferSource buffer, Vec3 cam, float pt,
                                   BlockPos sp, BlockState st) {
        return drawPlugAt(mc, level, pose, buffer, cam, pt, sp, Outlets.plugPose(st, SocketBlock.CORD_POSITIVE));
    }

    public static boolean drawPlugAt(Minecraft mc, ClientLevel level, PoseStack pose, MultiBufferSource buffer, Vec3 cam, float pt,
                                     BlockPos sp, @Nullable Matrix4f local) {
        if (local == null)
            return false;
        if (plugLook == null)
            plugLook = ReelPlugItem.look(true);
        Pose3dc sub = WallSpace.renderPose(level, sp, pt);
        Vec3 o = WallSpace.toWorld(sub, Vec3.ZERO);
        if (WallSpace.toWorld(sub, sp.getCenter()).distanceToSqr(cam) > PLUG_RANGE * PLUG_RANGE)
            return false;
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        if (sub != null) {
            Vec3 ex = WallSpace.rotate(sub, new Vec3(1, 0, 0)), ey = WallSpace.rotate(sub, new Vec3(0, 1, 0)),
                    ez = WallSpace.rotate(sub, new Vec3(0, 0, 1));
            Matrix4f m = new Matrix4f((float) ex.x, (float) ex.y, (float) ex.z, 0, (float) ey.x, (float) ey.y, (float) ey.z, 0,
                    (float) ez.x, (float) ez.y, (float) ez.z, 0, (float) o.x, (float) o.y, (float) o.z, 1);
            pose.mulPose(m);
        }
        pose.translate(sp.getX(), sp.getY(), sp.getZ());
        pose.mulPose(local);
        pose.translate(0.5f, 0.5f, 0.5f);
        mc.getItemRenderer().renderStatic(plugLook, ItemDisplayContext.NONE, LevelRenderer.getLightColor(level, sp),
                OverlayTexture.NO_OVERLAY, pose, buffer, level, 0);
        pose.popPose();
        return true;
    }

    public static void drawPlugPreview(ClientLevel level, PoseStack pose, Vec3 cam, float pt, BlockPos sp, Vec3 towards) {
        BlockState st = level.getBlockState(sp);
        if (!(st.getBlock() instanceof SocketBlock))
            return;
        Pose3dc sub = WallSpace.renderPose(level, sp, pt);
        Vector3f a = SocketBlock.plugAxis(st);
        Vec3 centre = WallSpace.toWorld(sub, sp.getCenter());
        Vec3 axis = WallSpace.rotate(sub, new Vec3(a.x, a.y, a.z));
        st = st.setValue(SocketBlock.PLUG, SocketPlugs.side(centre, axis, towards));
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        if (drawPlug(mc, level, pose, buffer, cam, pt, sp, st))
            buffer.endBatch();
    }

    private static long heldLogged;

    private record Source(Vec3 from, Vec3 fromOut, WireType type, double length, BlockPos skip) {}

    private static void renderHeld(RenderLevelStageEvent event, Minecraft mc, LocalPlayer local, ClientLevel level) {
        Vec3 cam = event.getCamera().getPosition();
        for (AbstractClientPlayer p : level.players()) {
            ItemStack held = p.getMainHandItem();
            if (!(held.getItem() instanceof ReelPlugItem) || p != local && p.position().distanceToSqr(cam) > PLUG_RANGE * PLUG_RANGE)
                continue;
            renderHeldBy(event, mc, p, p == local, level, held);
        }
    }

    private static @Nullable Source source(ClientLevel level, Player holder, ItemStack held, float pt) {
        if (LooseCordClient.isLooseCord(held)) {
            LooseCordClient.ReelClientSource c = LooseCordClient.heldSource(level, holder, held, pt);
            return c == null ? null : new Source(c.from(), c.fromOut(), c.type(), c.length(), c.skip());
        }
        CableReelBlockEntity be = ReelPlugItem.reel(level, held);
        if (be == null)
            return null;
        WireType type = be.wireType();
        if (type == null)
            return null;
        CableReelBlock block = be.block();
        Vec3 local = block.localNode(be.getBlockState(), block.outletId());
        Pose3dc reelPose = WallSpace.renderPose(level, be.getBlockPos(), pt);
        Vec3 from = WallSpace.toWorld(reelPose, Vec3.atLowerCornerOf(be.getBlockPos()).add(local));
        Vec3 fromOut = WallSpace.rotate(reelPose, block.outletDir(be.getBlockState()));
        return new Source(from, fromOut, type, be.stored(), be.getBlockPos());
    }

    private static void renderHeldBy(RenderLevelStageEvent event, Minecraft mc, Player player, boolean isLocal, ClientLevel level, ItemStack held) {
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Source src = source(level, player, held, pt);
        if (src == null)
            return;
        WireType type = src.type();
        Vec3 from = src.from();
        Vec3 cam = event.getCamera().getPosition();
        Vec3 guess = handPos(mc, player, event.getCamera(), pt);
        HeldEndTracker.Seen seen = null;
        if (isLocal) {
            Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(event.getPoseStack().last().pose());
            seen = HeldEndTracker.world(mc.options.getCameraType().isFirstPerson(), new Vector3d(cam.x, cam.y, cam.z),
                    view, new Matrix4f(RenderSystem.getProjectionMatrix()));
        }
        boolean drawn = seen != null && guess.distanceToSqr(seen.at().x, seen.at().y, seen.at().z) < 2.5 * 2.5;
        Vec3 hand = drawn ? new Vec3(seen.at().x, seen.at().y, seen.at().z) : guess;
        double distance = from.distanceTo(hand);
        if (distance < 0.05)
            return;
        double stored = src.length();
        WireData look = WireData.ofLength(type, ReelMath.followLength(distance, Math.max(stored, distance)));
        List<Vec3> points = drawn ? ReelWire.points(from, src.fromOut(), hand, seen.out(), CablePath.HELD_RUN, look.getSag(distance), Double.NEGATIVE_INFINITY,
                level, src.skip())
                : ReelWire.points(type, from, hand, look.getSag(distance), Double.NEGATIVE_INFINITY);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        ReelWire.render(points, pose, buffer, type, level);
        long now = System.currentTimeMillis();
        if (isLocal && now - heldLogged > 5000) {
            heldLogged = now;
            Voltworks.LOGGER.info("[voltworks] held wire: end={} (model {}, guess {}, captured {}) outlet={} segments={} camera={}",
                    hand, drawn ? "as drawn, cord axis " + seen.out() : "not captured, fallback", guess, seen, from, points.size() - 1,
                    mc.options.getCameraType());
        }
        pose.popPose();
        buffer.endBatch(type.renderType());

        if (isLocal && distance > stored && !mc.isPaused())
            for (Vec3 p : points)
                if (level.random.nextInt(9) == 0)
                    level.addParticle(new DustParticleOptions(new Vector3f(0.9f, 0.3f, 0.3f), 0.8f), p.x, p.y, p.z, 0, 0, 0);
    }

    static Vec3 handPos(Minecraft mc, Player player, Camera camera, float pt) {
        int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        float swing = Mth.sin(Mth.sqrt(player.getAttackAnim(pt)) * Mth.PI);
        if (mc.options.getCameraType().isFirstPerson() && player == mc.player) {
            double fov = 960.0 / mc.options.fov().get();
            Vec3 near = camera.getNearPlane().getPointOnPlane(side * 0.525f, -0.1f).scale(fov)
                    .yRot(swing * 0.5f).xRot(-swing * 0.7f);
            return player.getEyePosition(pt).add(near);
        }
        float body = Mth.lerp(pt, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        double sin = Mth.sin(body), cos = Mth.cos(body);
        double scale = player.getScale();
        double across = side * 0.35 * scale, forward = 0.8 * scale;
        double crouch = player.isCrouching() ? -0.1875 : 0;
        return player.getEyePosition(pt).add(-cos * across - sin * forward, crouch - 0.45 * scale, -sin * across + cos * forward);
    }
}
