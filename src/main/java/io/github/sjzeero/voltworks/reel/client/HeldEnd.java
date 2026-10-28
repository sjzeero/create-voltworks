package io.github.sjzeero.voltworks.reel.client;

import io.github.sjzeero.voltworks.reel.LyingEnd;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

public final class HeldEnd {
    private HeldEnd() {}

    public static Vector3dc backPx(boolean cord) {
        return (cord ? LyingEnd.PLUG : LyingEnd.LUG).backPx();
    }

    public static Vector3dc outPx(boolean cord) {
        LyingEnd.Lie lie = cord ? LyingEnd.PLUG : LyingEnd.LUG;
        return new Vector3d(lie.backDir()).mul(OUT_PX).add(lie.backPx());
    }

    public static final float OUT_PX = 2;

    public static Vector3f eye(Matrix4fc modelView, Matrix4fc pose, Vector3fc px) {
        return modelView.mul(pose, new Matrix4f()).transformPosition(px.x() / 16f, px.y() / 16f, px.z() / 16f, new Vector3f());
    }

    public static Vector3f ndc(Matrix4fc projection, Vector3fc eye) {
        Vector4f c = projection.transform(new Vector4f(eye, 1));
        return new Vector3f(c.x / c.w, c.y / c.w, c.z / c.w);
    }

    public static Vector3f reproject(Vector3fc eye, Matrix4fc from, Matrix4fc to) {
        Vector3f screen = ndc(from, eye);
        float depth = ndc(to, new Vector3f(0, 0, eye.z())).z;
        Vector4f p = to.invert(new Matrix4f()).transform(new Vector4f(screen.x, screen.y, depth, 1));
        return new Vector3f(p.x / p.w, p.y / p.w, p.z / p.w);
    }

    public static Vector3d world(Vector3dc cam, Matrix4fc view, Vector3fc eye) {
        Vector3f rel = view.invert(new Matrix4f()).transformPosition(eye, new Vector3f());
        return new Vector3d(cam).add(rel.x, rel.y, rel.z);
    }
}
