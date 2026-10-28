package io.github.sjzeero.voltworks.reel.client;

import io.github.sjzeero.voltworks.reel.LyingEnd;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 * Where the cable goes into the end model drawn in the hand, as plain matrix maths (tested without a game).
 * <p>
 * The held model is captured while it is drawn: {@link #eye} takes the matrices the item renderer really uses for it
 * (model-view times the item pose, which already holds the arm, bob, swing, equip and the model's display
 * transform) to the eye-space position of the model's cord pixel. A first-person item is drawn after the world with
 * its own projection (fixed 70 degree field of view), so {@link #reproject} moves that point to the spot the world
 * projection shows on the same screen pixel at the same depth, and {@link #world} turns the eye-space point into
 * a world position with the world camera. The wire drawn in the world then ends on the pixel where the cord leaves
 * the model, whatever the field of view, bob or swing.
 */
public final class HeldEnd {
    private HeldEnd() {}

    /** The cord pixel of the held end model (plug or lug): the same back points the lying ends use. */
    public static Vector3dc backPx(boolean cord) {
        return (cord ? LyingEnd.PLUG : LyingEnd.LUG).backPx();
    }

    /** Eye-space position of model pixel {@code px} (0..16) drawn with {@code pose} under {@code modelView}. */
    public static Vector3f eye(Matrix4fc modelView, Matrix4fc pose, Vector3fc px) {
        return modelView.mul(pose, new Matrix4f()).transformPosition(px.x() / 16f, px.y() / 16f, px.z() / 16f, new Vector3f());
    }

    /** Normalised device coordinates of an eye-space point. */
    public static Vector3f ndc(Matrix4fc projection, Vector3fc eye) {
        Vector4f c = projection.transform(new Vector4f(eye, 1));
        return new Vector3f(c.x / c.w, c.y / c.w, c.z / c.w);
    }

    /**
     * The eye-space point that projection {@code to} shows on the same screen point as {@code from} shows {@code eye},
     * at (about) the same depth.
     */
    public static Vector3f reproject(Vector3fc eye, Matrix4fc from, Matrix4fc to) {
        Vector3f screen = ndc(from, eye);
        float depth = ndc(to, new Vector3f(0, 0, eye.z())).z;
        Vector4f p = to.invert(new Matrix4f()).transform(new Vector4f(screen.x, screen.y, depth, 1));
        return new Vector3f(p.x / p.w, p.y / p.w, p.z / p.w);
    }

    /** World position of an eye-space point, for a camera at {@code cam} whose world-to-eye rotation is {@code view}. */
    public static Vector3d world(Vector3dc cam, Matrix4fc view, Vector3fc eye) {
        Vector3f rel = view.invert(new Matrix4f()).transformPosition(eye, new Vector3f());
        return new Vector3d(cam).add(rel.x, rel.y, rel.z);
    }
}
