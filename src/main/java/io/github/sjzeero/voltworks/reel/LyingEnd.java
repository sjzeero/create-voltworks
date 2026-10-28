package io.github.sjzeero.voltworks.reel;

import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * How a lying cable end model sits on the ground, shared by the renderer (model pose) and the cable (where it plugs
 * into the back of the model), so the two can't drift apart. Free of Minecraft classes for unit tests.
 * <p>
 * Model pixels p (0..16, as in the item model JSON) map to entity space (blocks, origin = entity position on the
 * ground) by {@code pose(yaw) * (p / 16 - 0.5)}; the {@code - 0.5} is the centring ItemRenderer applies itself.
 */
public final class LyingEnd {
    private LyingEnd() {}

    /**
     * @param base    model rotation about the model centre before lying down
     * @param axis    yaw of the model direction that points back to the reel (0 = +Z, PI/2 = +X)
     * @param floorPx lowest model y after {@code base}, in pixels: it is put on the ground
     * @param backPx  where the cable goes in, in the model's own pixels (as in the JSON)
     * @param backDir the cord axis there, pointing out of the model (unit, model axes): the cable leaves along it
     */
    public record Lie(Quaterniond base, double axis, double floorPx, Vector3dc backPx, Vector3dc backDir) {}

    /** cord_plug.json: standing in a floor socket, blades down, cord to +X. On the ground it lies on its back, blades up. */
    public static final Lie PLUG = new Lie(new Quaterniond().rotationX(Math.PI), Math.PI / 2, 16 - 5, new Vector3d(12, 4, 8), new Vector3d(1, 0, 0));
    /** cable_lug.json: flat already (y 6..8), ring to -Z, barrel back at z = 11 towards the reel. */
    public static final Lie LUG = new Lie(new Quaterniond(), 0, 6, new Vector3d(8, 7, 11), new Vector3d(0, 0, 1));

    /** Yaw that turns the model's reel side towards the reel, {@code (dx, dz)} from the end to the reel outlet. */
    public static double yaw(Lie lie, double dx, double dz, double fallback) {
        double yaw = Math.hypot(dx, dz) > 0.05 ? Math.atan2(dx, dz) : fallback;
        return yaw - lie.axis();
    }

    /** Entity space from centred model space (ItemRenderer's own -0.5 not included). */
    public static Matrix4d pose(Lie lie, double yaw) {
        return new Matrix4d().rotationY(yaw).translate(0, (8 - lie.floorPx()) / 16, 0).rotate(lie.base());
    }

    /** A model pixel point in entity space, through the same matrix the model is drawn with. */
    public static Vector3d toEntity(Matrix4d pose, Vector3dc px) {
        return pose.transformPosition(new Vector3d(px).div(16).sub(0.5, 0.5, 0.5));
    }

    /** Where the cable goes into the lying model, relative to the entity position. */
    public static Vector3d attach(Lie lie, double yaw) {
        return toEntity(pose(lie, yaw), lie.backPx());
    }

    /** A model direction (the cord axis) in entity space, through the same matrix, unit length. */
    public static Vector3d direction(Matrix4d pose, Vector3dc dir) {
        return pose.transformDirection(new Vector3d(dir)).normalize();
    }
}
