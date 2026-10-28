package io.github.sjzeero.voltworks.reel.client;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.QuadraticWireHelper;
import com.george_vi.electroenergetics.simulation.WireType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import io.github.sjzeero.voltworks.reel.CablePath;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

/**
 * A free-hanging reel cable from the outlet all the way to an end point, in Electro Energetics' wire look (its wire
 * segment model, render type and lighting).
 * <p>
 * Not {@code WireRenderer.forceRenderWire}: for wire types that don't "scale the last" segment (most of them) it
 * leaves out the first and the last segment - those are covered by the insulator/end-cap models on Electro
 * Energetics' own connectors - so a cable drawn with it stops half a block (one segment) short of the hand or of the
 * lying end. Here every segment is drawn, end caps are not.
 * <p>
 * Points are world positions; {@code pose} must map world space to the camera (translate by minus the camera).
 */
public final class ReelWire {
    private ReelWire() {}

    /** The sagging points from {@code from} to {@code to}, both ends included, none lower than {@code floor}. */
    public static List<Vec3> points(WireType type, Vec3 from, Vec3 to, float sag, double floor) {
        List<Vec3> raw = type.shouldScaleLast() ? QuadraticWireHelper.cablePoints(from, to, sag) : QuadraticWireHelper.cablePointsRaw(from, to, sag);
        List<Vec3> out = new ArrayList<>(raw.size() + 2);
        out.add(from);
        for (Vec3 p : raw)
            if (p.distanceToSqr(from) > 1.0E-8)
                out.add(p.y < floor ? new Vec3(p.x, floor, p.z) : p);
        out.add(to);
        return out;
    }

    /**
     * The cable into an end model: straight out of the model along its cord axis {@code out} for {@code run}, then a
     * smooth sagging curve to {@code from} (see {@link CablePath}); none of it lower than {@code floor} or in the
     * ground under it.
     */
    public static List<Vec3> points(Vec3 from, Vec3 fromOut, Vec3 to, Vector3dc out, double run, float dip, double floor,
                                    BlockGetter level, BlockPos reel) {
        List<Vec3> pts = new ArrayList<>();
        // the reel's own block (and the one its outlet is in) is not ground: the cable leaves through its side
        CablePath.Floor ground = terrain(level, floor, reel, BlockPos.containing(from));
        for (Vector3d p : CablePath.points(new Vector3d(from.x, from.y, from.z), new Vector3d(fromOut.x, fromOut.y, fromOut.z),
                CablePath.RUN, new Vector3d(to.x, to.y, to.z), out, run, CablePath.sagDepth(from.distanceTo(to), dip), ground))
            pts.add(new Vec3(p.x, p.y, p.z));
        return pts;
    }

    /**
     * The ground under a wire point: the top of the collision shape of the block the point is in (it is pushed out
     * on top) or of the block right under it, plus the wire's half thickness; never lower than {@code flat}. Blocks
     * in {@code skip} don't count.
     */
    public static CablePath.Floor terrain(BlockGetter level, double flat, BlockPos... skip) {
        return (x, y, z) -> {
            BlockPos at = BlockPos.containing(x, y, z);
            double top = Double.NEGATIVE_INFINITY;
            for (BlockPos q : new BlockPos[]{at, at.below()}) {
                if (java.util.Arrays.asList(skip).contains(q))
                    continue;
                VoxelShape shape = level.getBlockState(q).getCollisionShape(level, q);
                if (!shape.isEmpty()) {
                    top = q.getY() + shape.max(Direction.Axis.Y);
                    break;
                }
            }
            double lowest = top + CablePath.RADIUS;
            return Math.max(flat, y < lowest ? lowest : Double.NEGATIVE_INFINITY);
        };
    }

    public static void render(List<Vec3> points, PoseStack pose, MultiBufferSource buffer, WireType type, BlockAndTintGetter level) {
        if (points.size() < 2)
            return;
        VertexConsumer vb = buffer.getBuffer(type.renderType());
        for (int i = 0; i + 1 < points.size(); i++) {
            Vec3 a = points.get(i), b = points.get(i + 1);
            double len = a.distanceTo(b);
            if (len < 1.0E-5)
                continue;
            BlockPos pa = BlockPos.containing(a), pb = BlockPos.containing(b);
            int light = pa.equals(pb) ? LevelRenderer.getLightColor(level, pa)
                    : WireRenderer.maxLightLevel(LevelRenderer.getLightColor(level, pa), LevelRenderer.getLightColor(level, pb));
            SuperByteBuffer sbb = CachedBuffers.partial(type.getModel(), Blocks.ANDESITE.defaultBlockState());
            sbb.translate(a);
            sbb.rotateY((float) Mth.atan2(b.x - a.x, b.z - a.z));
            sbb.rotateX(-(float) Mth.atan2(b.y - a.y, Math.hypot(b.x - a.x, b.z - a.z)));
            sbb.scaleZ((float) (len * 2.0) + 0.02f); // the segment model is 8 px long
            sbb.light(light).renderInto(pose, vb);
        }
    }
}
