package io.github.sjzeero.voltworks.reel;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Motion of a lying cable end, free of Minecraft classes so it can be simulated in unit tests. Each tick: gravity,
 * then the rope (the wire on the drum), then the caller moves the entity by {@link Step#move} with block collision,
 * then {@link #friction}.
 * <p>
 * The rope only pulls, never pushes: while the end is within reach it does nothing. When this tick's motion would
 * carry the end past the reach, the motion is replaced by the move onto the sphere of that radius around the outlet
 * and the outward part of the velocity is dropped, so the end swings down and hangs below the outlet. Nothing here
 * ever adds upward speed: gravity is the only acceleration.
 */
public final class LooseEndPhysics {
    private LooseEndPhysics() {}

    public static final double GRAVITY = 0.04;
    public static final double AIR_DRAG = 0.98;
    public static final double GROUND_FRICTION = 0.5;
    /** Further than this past the reach (stuck behind a block) the wire pulls the end straight through. */
    public static final double SNAP_THROUGH = 2;

    /** What to do this tick: move by {@code move} (with collision) or, when {@code teleport}, set the position to it. */
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

    /** Velocity after the move: drag in the air, friction on the ground. */
    public static Vector3d friction(Vector3dc velocity, boolean onGround) {
        double f = onGround ? GROUND_FRICTION : AIR_DRAG;
        return new Vector3d(velocity.x() * f, velocity.y() * AIR_DRAG, velocity.z() * f);
    }
}
