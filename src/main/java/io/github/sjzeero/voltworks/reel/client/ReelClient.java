package io.github.sjzeero.voltworks.reel.client;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.QuadraticWireHelper;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sjzeero.voltworks.reel.CableReelBlock;
import io.github.sjzeero.voltworks.reel.CableReelBlockEntity;
import io.github.sjzeero.voltworks.reel.ReelMath;
import io.github.sjzeero.voltworks.reel.ReelPlugItem;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import net.createmod.catnip.data.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

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

    // ---- cable end in the hand -----------------------------------------------------------------------------------

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES)
            return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null)
            return;
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
        Vec3 hand = handPos(mc, player, pt);
        double distance = from.distanceTo(hand);
        if (distance < 0.05)
            return;
        double stored = be.stored();
        WireData look = WireData.ofLength(type, ReelMath.followLength(distance, Math.max(stored, distance)));
        float sag = look.getSag(distance);
        List<Vec3> points = type.shouldScaleLast()
                ? QuadraticWireHelper.cablePoints(from, hand, sag)
                : QuadraticWireHelper.cablePointsRaw(from, hand, sag);

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        WireRenderer.forceRenderWire(points, from, hand, pose, buffer, type, level);
        pose.popPose();
        buffer.endBatch(type.renderType());

        // stretched further than the wire on the drum: red sparks like Electro Energetics' spool preview
        if (distance > stored && !mc.isPaused())
            for (Vec3 p : points)
                if (level.random.nextInt(9) == 0)
                    level.addParticle(new DustParticleOptions(new Vector3f(0.9f, 0.3f, 0.3f), 0.8f), p.x, p.y, p.z, 0, 0, 0);
    }

    /** Roughly where the held cable end is: in front of the eyes, low, on the main-hand side. */
    private static Vec3 handPos(Minecraft mc, LocalPlayer player, float pt) {
        Vec3 eye = player.getEyePosition(pt);
        Vec3 look = player.getViewVector(pt);
        float yaw = (float) Math.toRadians(player.getViewYRot(pt));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        double side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        if (!mc.options.getCameraType().isFirstPerson())
            return player.getPosition(pt).add(0, player.getBbHeight() * 0.55, 0).add(right.scale(0.35 * side)).add(look.multiply(0.25, 0, 0.25));
        return eye.add(look.scale(0.6)).add(right.scale(0.3 * side)).add(0, -0.35, 0);
    }
}
