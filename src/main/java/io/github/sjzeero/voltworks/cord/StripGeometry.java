package io.github.sjzeero.voltworks.cord;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class StripGeometry {
    private StripGeometry() {}

    public static final double CORD_LENGTH = 4.0;
    public static final int INPUT = 4;
    public static final int[] OUTLETS = {6, 8, 10};
    public static final int NODES = 12;
    public static final int[] CELL_X = {-1, 5, 11, 17};
    public static final int BODY_Z0 = 5, BODY_Z1 = 11, BODY_TOP = 3;
    public static final int[] GROMMET = {-3, 0, 7, -1, 2, 9};
    public static final Vector3dc INPUT_NODE = new Vector3d(-3, 1, 8);
    public static final Vector3dc INPUT_DIR = new Vector3d(-1, 0, 0);
    public static final Vector3dc CORD_DIR = new Vector3d(0, 0, 1);
    public static final Vector3dc PLUG_BACK = new Vector3d(12, 4, 8);
    public static final double INPUT_ARM = Math.hypot(INPUT_NODE.x() - 8, INPUT_NODE.z() - 8) / 16;
    public static final double CENTER_REACH = CORD_LENGTH - INPUT_ARM;

    public static int index(int supply) {
        for (int i = 0; i < OUTLETS.length; i++)
            if (OUTLETS[i] == (supply & ~1))
                return i;
        return -1;
    }

    public static boolean isOutlet(int id) {
        return index(id) >= 0;
    }

    public static boolean isInput(int id) {
        return id == INPUT || id == INPUT + 1;
    }

    public static boolean isStripNode(int id) {
        return id >= INPUT && id < NODES;
    }

    public static int centerX(int supply) {
        int i = index(supply);
        return (CELL_X[i] + CELL_X[i + 1]) / 2;
    }

    public static Vector3d plugBack(int supply) {
        return new Vector3d(centerX(supply), PLUG_BACK.y(), 8 + (PLUG_BACK.x() - 8));
    }

    public static Vector3d node(int id) {
        return isOutlet(id) ? plugBack(id) : new Vector3d(INPUT_NODE);
    }

    public static Vector3d dir(int id) {
        return new Vector3d(isOutlet(id) ? CORD_DIR : INPUT_DIR);
    }

    public static Vector3d local(Vector3dc px) {
        return new Vector3d((px.x() - 8) / 16, px.y() / 16, (px.z() - 8) / 16);
    }

    public static Vector3d world(Vector3dc at, double yaw, Vector3dc px) {
        return local(px).rotateY(yaw).add(at);
    }

    public static Vector3d turn(double yaw, Vector3dc dir) {
        return new Vector3d(dir).rotateY(yaw);
    }

    public static Vector3d unturn(double yaw, Vector3dc offset) {
        Vector3d v = new Vector3d(offset).rotateY(-yaw);
        return new Vector3d(v.x * 16 + 8, v.y * 16, v.z * 16 + 8);
    }

    public static double yawToward(double dx, double dz, double fallback) {
        return Math.hypot(dx, dz) > 0.05 ? Math.atan2(dz, -dx) : fallback;
    }

    public static int outletAt(Vector3dc px) {
        if (px.z() < BODY_Z0 - 3 || px.z() > BODY_Z1 + 3)
            return -1;
        for (int i = 0; i < OUTLETS.length; i++)
            if (px.x() >= CELL_X[i] && px.x() < CELL_X[i + 1])
                return OUTLETS[i];
        return -1;
    }

    public static Matrix4f plugPose(Vector3dc at, double yaw, Vector3dc origin, int supply) {
        return new Matrix4f()
                .translate((float) (at.x() - origin.x()), (float) (at.y() - origin.y()), (float) (at.z() - origin.z()))
                .rotateY((float) yaw)
                .translate((centerX(supply) - 8) / 16f, 0.5f, 0)
                .rotateY((float) (-Math.PI / 2))
                .translate(-0.5f, -0.5f, -0.5f);
    }

    public static int[] plugFootprint(int supply) {
        int cx = centerX(supply);
        return new int[]{cx - 3, cx + 3, 6, 10};
    }
}
