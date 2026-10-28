package io.github.sjzeero.voltworks.reel;

import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class LooseEndPhysics {
    private LooseEndPhysics() {}

    public static final double GRAVITY = 0.04;
    public static final double AIR_DRAG = 0.98;
    public static final double GROUND_FRICTION = 0.5;
    public static final double SNAP_THROUGH = 2;

    public record Step(Vector3d move, Vector3d velocity, boolean teleport) {}

    public static Step step(Vector3dc pos, Vector3dc velocity, Vector3dc outlet, double reach) {
        Vector3d v = new Vector3d(velocity).sub(0, GRAVITY, 0);
        Vector3d next = new Vector3d(pos).add(v);
        Vector3d off = new Vector3d(next).sub(outlet);
        double dist = off.length();
        if (dist <= reach + ReelMath.EPS || dist < 1.0E-6)
            return new Step(v, v, false);
        Vector3d n = off.div(dist);
        Vector3d onSphere = new Vector3d(n).mul(Math.max(0, reach)).add(outlet);
        double out = v.dot(n);
        Vector3d kept = out > 0 ? new Vector3d(v).sub(new Vector3d(n).mul(out)) : new Vector3d(v);
        if (pos.distance(outlet) > reach + SNAP_THROUGH)
            return new Step(onSphere, new Vector3d(), true);
        return new Step(onSphere.sub(pos), kept, false);
    }

    public static Vector3d friction(Vector3dc velocity, boolean onGround) {
        double f = onGround ? GROUND_FRICTION : AIR_DRAG;
        return new Vector3d(velocity.x() * f, velocity.y() * AIR_DRAG, velocity.z() * f);
    }
}
