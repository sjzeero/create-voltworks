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
import io.github.sjzeero.voltworks.cord.SocketBlock;
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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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

/**
 * Client side of the cable reels.
 * <ul>
 *   <li>The cable end carried in the hand is drawn with Electro Energetics' own wire renderer and the reel's wire type
 *   (the same segment model and sag as a laid wire), from the reel outlet to the hand. Stretched past the wire on the
 *   drum it gets red sparks, like Electro Energetics' spool preview.</li>
 *   <li>A laid reel cable takes its drawn length from the distance the client sees each frame, the way the reel
 *   pays out and winds in on the server, so it hangs the same while a Sable structure carries one end around instead
 *   of waiting for (and snapping to) length updates from the server.</li>
 * </ul>
 */
public final class ReelClient {
    private ReelClient() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ReelClient::render);
        HeldEndTracker.init();
    }

    // ---- laid cables -------------------------------------------------------------------------------------------

    /** The reel whose hidden outlet this node is, on the client, or null. */
    private static @Nullable CableReelBlockEntity reelAt(Level level, InWorldNode node) {
        if (!level.isLoaded(node.sourcePos()))
            return null;
        BlockState state = level.getBlockState(node.sourcePos());
        if (!(state.getBlock() instanceof CableReelBlock reel) || !reel.isOutlet(node.id()))
            return null;
        return level.getBlockEntity(node.sourcePos()) instanceof CableReelBlockEntity be ? be : null;
    }

    /** Gives a reel cable the length it has at this frame's distance; other wires are left alone. */
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

    /** For Electro Energetics' immediate (non-Flywheel) wire renderer, which draws straight from the connection list. */
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

    // ---- plugs in sockets --------------------------------------------------------------------------------------

    private static final double PLUG_RANGE = 96;
    private static @Nullable ItemStack plugLook;

    /**
     * A cord reel plugged into a socket: the plug model sits in the socket's slots (blades hidden in the socket), turned
     * by the socket's {@link SocketBlock#PLUG} state so the cord leaves towards the reel, and the cord - whose node the
     * socket moved to the plug's back - ends in it. Ordinary cords into the same socket stay as they are.
     */
    private static void renderPlugs(RenderLevelStageEvent event, Minecraft mc, ClientLevel level) {
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        if (plugLook == null)
            plugLook = ReelPlugItem.look(true);
        Set<BlockPos> done = new HashSet<>();
        boolean any = false;
        for (Pair<InWorldNodeConnection, WireData> w : WireRenderer.getAllConnections()) {
            InWorldNodeConnection c = w.getFirst();
            InWorldNode socket = c.node2();
            CableReelBlockEntity be = reelAt(level, c.node1());
            if (be == null) {
                be = reelAt(level, c.node2());
                socket = c.node1();
            }
            if (be == null || !be.block().cord)
                continue;
            BlockPos sp = socket.sourcePos();
            if (!done.add(sp) || !level.isLoaded(sp))
                continue;
            BlockState st = level.getBlockState(sp);
            if (!(st.getBlock() instanceof SocketBlock) || st.getValue(SocketBlock.PLUG) == SocketBlock.Plug.NONE)
                continue;
            Pose3dc sub = WallSpace.renderPose(level, sp, pt);
            Vec3 o = WallSpace.toWorld(sub, Vec3.ZERO);
            if (WallSpace.toWorld(sub, sp.getCenter()).distanceToSqr(cam) > PLUG_RANGE * PLUG_RANGE)
                continue;
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            if (sub != null) {
                Vec3 ex = WallSpace.rotate(sub, new Vec3(1, 0, 0)), ey = WallSpace.rotate(sub, new Vec3(0, 1, 0)),
                        ez = WallSpace.rotate(sub, new Vec3(0, 0, 1));
                Matrix4f m = new Matrix4f((float) ex.x, (float) ex.y, (float) ex.z, 0, (float) ey.x, (float) ey.y, (float) ey.z, 0,
                        (float) ez.x, (float) ez.y, (float) ez.z, 0, (float) o.x, (float) o.y, (float) o.z, 1);
                pose.mulPose(m);
            }
            pose.translate(sp.getX() + 0.5, sp.getY() + 0.5, sp.getZ() + 0.5);
            pose.mulPose(SocketBlock.modelRotation(st));
            mc.getItemRenderer().renderStatic(plugLook, ItemDisplayContext.NONE, LevelRenderer.getLightColor(level, sp),
                    OverlayTexture.NO_OVERLAY, pose, buffer, level, 0);
            pose.popPose();
            any = true;
        }
        if (any)
            buffer.endBatch();
    }

    // ---- cable end in the hand -----------------------------------------------------------------------------------

    private static long heldLogged;

    private static void renderHeld(RenderLevelStageEvent event, Minecraft mc, LocalPlayer player, ClientLevel level) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ReelPlugItem))
            return;
        CableReelBlockEntity be = ReelPlugItem.reel(level, held);
        if (be == null)
            return;
        WireType type = be.wireType();
        if (type == null)
            return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(true);

        CableReelBlock block = be.block();
        Vec3 local = block.localNode(be.getBlockState(), block.outletId());
        Vec3 from = WallSpace.toWorld(WallSpace.renderPose(level, be.getBlockPos(), pt), Vec3.atLowerCornerOf(be.getBlockPos()).add(local));
        Vec3 cam = event.getCamera().getPosition();
        Vec3 guess = handPos(mc, player, event.getCamera(), pt);
        // the back of the end model as really drawn this frame (display transform, bob, swing, field of view)
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(event.getPoseStack().last().pose());
        HeldEndTracker.Seen seen = HeldEndTracker.world(mc.options.getCameraType().isFirstPerson(), new Vector3d(cam.x, cam.y, cam.z),
                view, new Matrix4f(RenderSystem.getProjectionMatrix()));
        boolean drawn = seen != null && guess.distanceToSqr(seen.at().x, seen.at().y, seen.at().z) < 2.5 * 2.5;
        Vec3 hand = drawn ? new Vec3(seen.at().x, seen.at().y, seen.at().z) : guess;
        double distance = from.distanceTo(hand);
        if (distance < 0.05)
            return;
        double stored = be.stored();
        WireData look = WireData.ofLength(type, ReelMath.followLength(distance, Math.max(stored, distance)));
        // out of the back of the model along its cord axis, then a smooth curve to the reel
        List<Vec3> points = drawn ? ReelWire.points(from, hand, seen.out(), CablePath.HELD_RUN, look.getSag(distance), Double.NEGATIVE_INFINITY, level)
                : ReelWire.points(type, from, hand, look.getSag(distance), Double.NEGATIVE_INFINITY);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        ReelWire.render(points, pose, buffer, type, level);
        long now = System.currentTimeMillis();
        if (now - heldLogged > 5000) {
            heldLogged = now;
            Voltworks.LOGGER.info("[voltworks] held wire: end={} (model {}, guess {}, captured {}) outlet={} segments={} camera={}",
                    hand, drawn ? "as drawn, cord axis " + seen.out() : "not captured, fallback", guess, seen, from, points.size() - 1,
                    mc.options.getCameraType());
        }
        pose.popPose();
        buffer.endBatch(type.renderType());

        // stretched further than the wire on the drum: red sparks like Electro Energetics' spool preview
        if (distance > stored && !mc.isPaused())
            for (Vec3 p : points)
                if (level.random.nextInt(9) == 0)
                    level.addParticle(new DustParticleOptions(new Vector3f(0.9f, 0.3f, 0.3f), 0.8f), p.x, p.y, p.z, 0, 0, 0);
    }

    /**
     * Fallback when the held model was not seen being drawn (hand hidden, another mod drawing items): where the held
     * cable end is, roughly, the way vanilla finds the hand holding a fishing rod line: in first person a point
     * on the camera's near plane at the main-hand corner (scaled by the field of view, so the wire runs into the held
     * item at the bottom of the screen instead of stopping in front of the crosshair), in third person the hand of
     * the body model.
     */
    private static Vec3 handPos(Minecraft mc, LocalPlayer player, Camera camera, float pt) {
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
