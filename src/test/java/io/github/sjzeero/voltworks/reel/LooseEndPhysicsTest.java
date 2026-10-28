package io.github.sjzeero.voltworks.reel;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LooseEndPhysicsTest {
    record Sim(Vector3d pos, Vector3d vel, double maxY) {}

    static Sim run(Vector3d start, Vector3d motion, Vector3d outlet, double reach, int ticks) {
        Vector3d pos = new Vector3d(start), vel = new Vector3d(motion);
        double maxY = pos.y;
        for (int t = 0; t < ticks; t++) {
            LooseEndPhysics.Step s = LooseEndPhysics.step(pos, vel, outlet, reach);
            vel = new Vector3d(s.velocity());
            if (s.teleport())
                pos = new Vector3d(s.move());
            else
                pos.add(s.move());
            boolean onGround = false;
            if (pos.y <= 0) {
                pos.y = 0;
                onGround = true;
                if (vel.y < 0)
                    vel.y = 0;
            }
            vel = LooseEndPhysics.friction(vel, onGround);
            if (onGround && vel.y < 0)
                vel.y = 0;
            maxY = Math.max(maxY, pos.y);
        }
        return new Sim(pos, vel, maxY);
    }

    @Test
    void droppedBesideTheReelFallsAndStaysOnTheGround() {
        Sim s = run(new Vector3d(2, 1.5, 0), new Vector3d(), new Vector3d(0, 0.8, 0), 32, 200);
        assertEquals(0, s.pos().y, 1e-9);
        assertEquals(1.5, s.maxY(), 1e-9, "must never rise");
        assertTrue(s.vel().length() < 1e-3);
    }

    @Test
    void droppedInTheSkyFallsAllTheWayDown() {
        Sim s = run(new Vector3d(10, 25, 0), new Vector3d(), new Vector3d(0, 0.8, 0), 32, 400);
        assertEquals(0, s.pos().y, 1e-9);
        assertEquals(25, s.maxY(), 1e-9);
    }

    @Test
    void thrownUpwardsStillComesDown() {
        Sim s = run(new Vector3d(1, 1, 0), new Vector3d(0.2, 0.4, 0), new Vector3d(0, 0.8, 0), 32, 200);
        assertEquals(0, s.pos().y, 1e-9);
        assertTrue(s.maxY() < 1 + 0.4 * 0.4 / (2 * LooseEndPhysics.GRAVITY) + 0.5, "only as high as the throw");
    }

    @Test
    void pastTheReachItHangsBelowTheOutletNeverAboveIt() {
        Vector3d outlet = new Vector3d(0, 10, 0);
        Vector3d pos = new Vector3d(5, 10, 0), vel = new Vector3d();
        double maxY = pos.y;
        for (int t = 0; t < 600; t++) {
            LooseEndPhysics.Step s = LooseEndPhysics.step(pos, vel, outlet, 5);
            vel = new Vector3d(s.velocity());
            pos = s.teleport() ? new Vector3d(s.move()) : pos.add(s.move());
            vel = LooseEndPhysics.friction(vel, false);
            maxY = Math.max(maxY, pos.y);
            assertTrue(pos.distance(outlet) <= 5 + 1e-6, "rope never longer than the wire");
        }
        assertTrue(maxY <= 10 + 1e-9, "never above the outlet: " + maxY);
        assertEquals(5, pos.y, 0.05, "hangs straight down");
        assertEquals(0, pos.x, 0.1);
    }

    @Test
    void tooFarAndStuckIsPulledBackOntoTheReach() {
        Vector3d outlet = new Vector3d(0, 0.8, 0);
        LooseEndPhysics.Step s = LooseEndPhysics.step(new Vector3d(20, 0, 0), new Vector3d(), outlet, 10);
        assertTrue(s.teleport());
        assertEquals(10, s.move().distance(outlet), 1e-6);
        assertTrue(s.move().y <= 0.8);
    }

    @Test
    void theRopeNeverAddsSpeed() {
        Vector3d outlet = new Vector3d(0, 3, 0);
        for (double x = -6; x <= 6; x += 0.5)
            for (double y = -6; y <= 6; y += 0.5)
                for (double vy = -0.5; vy <= 0.5; vy += 0.25) {
                    Vector3d v = new Vector3d(0.1, vy, 0);
                    LooseEndPhysics.Step s = LooseEndPhysics.step(new Vector3d(x, y, 1), v, outlet, 4);
                    double free = new Vector3d(v).sub(0, LooseEndPhysics.GRAVITY, 0).length();
                    assertTrue(s.velocity().length() <= free + 1e-9, "speed added at " + x + "," + y + " vy " + vy);
                }
    }
}
