package io.github.sjzeero.voltworks.reel;

import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class LyingEnd {
    private LyingEnd() {}

    public record Lie(Quaterniond base, double axis, double floorPx, Vector3dc backPx, Vector3dc backDir) {}

    public static final Lie PLUG = new Lie(new Quaterniond().rotationX(Math.PI), Math.PI / 2, 16 - 5, new Vector3d(12, 4, 8), new Vector3d(1, 0, 0));
    public static final Lie LUG = new Lie(new Quaterniond(), 0, 6, new Vector3d(8, 7, 11), new Vector3d(0, 0, 1));

    public static double yaw(Lie lie, double dx, double dz, double fallback) {
        double yaw = Math.hypot(dx, dz) > 0.05 ? Math.atan2(dx, dz) : fallback;
        return yaw - lie.axis();
    }

    public static Matrix4d pose(Lie lie, double yaw) {
        return new Matrix4d().rotationY(yaw).translate(0, (8 - lie.floorPx()) / 16, 0).rotate(lie.base());
    }

    public static Vector3d toEntity(Matrix4d pose, Vector3dc px) {
        return pose.transformPosition(new Vector3d(px).div(16).sub(0.5, 0.5, 0.5));
    }

    public static Vector3d attach(Lie lie, double yaw) {
        return toEntity(pose(lie, yaw), lie.backPx());
    }

    public static Vector3d direction(Matrix4d pose, Vector3dc dir) {
        return pose.transformDirection(new Vector3d(dir)).normalize();
    }
}
