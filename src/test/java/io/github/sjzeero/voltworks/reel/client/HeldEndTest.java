package io.github.sjzeero.voltworks.reel.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.sjzeero.voltworks.reel.CablePath;
import io.github.sjzeero.voltworks.reel.LyingEnd;
import io.github.sjzeero.voltworks.reel.ReelAssetsTest;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeldEndTest {
    static final float DEG = (float) (Math.PI / 180);

    static Matrix4f perspective(float fovDeg) {
        return new Matrix4f().perspective(fovDeg * DEG, 16f / 9f, 0.05f, 512f);
    }

    static float[] vec(JsonObject o, String key, float def) {
        JsonArray a = o.getAsJsonArray(key);
        return a == null ? new float[]{def, def, def} : new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    static JsonObject display(String model, String ctx) throws IOException {
        return ReelAssetsTest.json("assets/voltworks/models/item/" + model + ".json").getAsJsonObject("display").getAsJsonObject(ctx);
    }

    static void applyDisplay(Matrix4f m, JsonObject d, boolean leftHand) {
        float[] r = vec(d, "rotation", 0), t = vec(d, "translation", 0), s = vec(d, "scale", 1);
        int i = leftHand ? -1 : 1;
        m.translate(i * t[0] / 16f, t[1] / 16f, t[2] / 16f);
        m.rotate(new Quaternionf().rotationXYZ(r[0] * DEG, i * r[1] * DEG, i * r[2] * DEG));
        m.scale(s[0], s[1], s[2]);
    }

    static Matrix4f firstPerson(String model, boolean right, float equip, float swing) throws IOException {
        int i = right ? 1 : -1;
        float sq = (float) Math.sqrt(swing);
        Matrix4f m = new Matrix4f();
        m.translate(i * -0.4f * (float) Math.sin(sq * Math.PI), 0.2f * (float) Math.sin(sq * 2 * Math.PI), -0.2f * (float) Math.sin(swing * Math.PI));
        m.translate(i * 0.56f, -0.52f + equip * -0.6f, -0.72f);
        float f = (float) Math.sin(swing * swing * Math.PI), f1 = (float) Math.sin(sq * Math.PI);
        m.rotateY(i * (45 + f * -20) * DEG).rotateZ(i * f1 * -20 * DEG).rotateX(f1 * -80 * DEG).rotateY(i * -45 * DEG);
        applyDisplay(m, display(model, right ? "firstperson_righthand" : "firstperson_lefthand"), !right);
        return m.translate(-0.5f, -0.5f, -0.5f);
    }

    @Test
    void sameScreenPointAtTheSameDepthWhateverTheFieldOfView() {
        Matrix4f hand = perspective(70);
        Vector3f eye = new Vector3f(0.31f, -0.27f, -0.66f);
        for (float fov : new float[]{30, 70, 90, 110}) {
            Matrix4f world = perspective(fov);
            Vector3f moved = HeldEnd.reproject(eye, hand, world);
            Vector3f a = HeldEnd.ndc(hand, eye), b = HeldEnd.ndc(world, moved);
            assertEquals(a.x, b.x, 1e-4, "x at fov " + fov);
            assertEquals(a.y, b.y, 1e-4, "y at fov " + fov);
            assertEquals(eye.z, moved.z, 1e-3, "depth at fov " + fov);
        }
    }

    @Test
    void wireEndIsDrawnOnTheCordPixelOfTheHeldModel() throws IOException {
        Matrix4f handProj = perspective(70), worldProj = perspective(95);
        Quaternionf camRot = new Quaternionf().rotationYXZ((float) Math.PI - 37 * DEG, 21 * DEG, 0);
        Matrix4f view = new Matrix4f().rotation(camRot.conjugate(new Quaternionf()));
        Vector3d cam = new Vector3d(100.5, 64.62, -20.25);
        for (String model : new String[]{"cord_plug", "cable_lug"})
            for (boolean right : new boolean[]{true, false})
                for (float swing : new float[]{0, 0.3f, 0.8f}) {
                    Vector3f px = HeldEnd.backPx(model.equals("cord_plug")).get(new Vector3f());
                    Matrix4f pose = new Matrix4f(view).invert().mul(firstPerson(model, right, 0, swing));
                    Vector3f eye = HeldEnd.eye(view, pose, px);
                    Vector3f expect = HeldEnd.ndc(handProj, eye);
                    Vector3d w = HeldEnd.world(cam, view, HeldEnd.reproject(eye, handProj, worldProj));
                    Vector3f rel = new Vector3f((float) (w.x - cam.x), (float) (w.y - cam.y), (float) (w.z - cam.z));
                    Vector3f got = HeldEnd.ndc(worldProj, view.transformPosition(rel, new Vector3f()));
                    String what = model + (right ? " right" : " left") + " swing " + swing;
                    assertEquals(expect.x, got.x, 1e-3, what);
                    assertEquals(expect.y, got.y, 1e-3, what);
                    assertTrue(Math.abs(expect.x) < 1 && Math.abs(expect.y) < 1, what + " on screen: " + expect);
                    assertTrue(w.distance(cam) < 1.5, what + " near the eye: " + w.distance(cam));
                    if (swing == 0) {
                        assertEquals(right, expect.x > 0, what + " in its own half of the screen: " + expect);
                        assertTrue(expect.y < 0, what + " below the crosshair: " + expect);
                    }
                }
    }

    static Vector2f screen(Matrix4f proj, Vector3f a, Vector3f b) {
        Vector3f na = HeldEnd.ndc(proj, a), nb = HeldEnd.ndc(proj, b);
        return new Vector2f(nb.x - na.x, nb.y - na.y).normalize();
    }

    @Test
    void heldCableLeavesAlongTheCordAxisOnScreen() throws IOException {
        Matrix4f handProj = perspective(70), worldProj = perspective(95);
        Matrix4f view = new Matrix4f().rotation(new Quaternionf().rotationYXZ((float) Math.PI - 120 * DEG, -15 * DEG, 0).conjugate());
        Vector3d cam = new Vector3d(-7.5, 70.62, 12.25);
        for (String model : new String[]{"cord_plug", "cable_lug"})
            for (boolean right : new boolean[]{true, false}) {
                boolean cord = model.equals("cord_plug");
                Matrix4f pose = new Matrix4f(view).invert().mul(firstPerson(model, right, 0, 0));
                Vector3f eye = HeldEnd.eye(view, pose, HeldEnd.backPx(cord).get(new Vector3f()));
                Vector3f eyeOut = HeldEnd.eye(view, pose, HeldEnd.outPx(cord).get(new Vector3f()));
                Vector3d at = HeldEnd.world(cam, view, HeldEnd.reproject(eye, handProj, worldProj));
                Vector3d out = HeldEnd.world(cam, view, HeldEnd.reproject(eyeOut, handProj, worldProj)).sub(at).normalize();
                Vector3d reel = new Vector3d(cam).add(4, -1.5, 3);
                List<Vector3d> p = CablePath.points(reel, at, out, CablePath.HELD_RUN,
                        CablePath.sagDepth(reel.distance(at), 1), CablePath.Floor.flat(Double.NEGATIVE_INFINITY));
                Vector3d next = p.get(p.size() - 2);
                Vector3f a = view.transformPosition(new Vector3f((float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z)), new Vector3f());
                Vector3f b = view.transformPosition(new Vector3f((float) (next.x - cam.x), (float) (next.y - cam.y), (float) (next.z - cam.z)), new Vector3f());
                Vector2f wire = screen(worldProj, a, b), axis = screen(handProj, eye, eyeOut);
                double deg = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, wire.dot(axis)))));
                assertTrue(deg < 3, model + (right ? " right" : " left") + ": first segment " + deg + " degrees off the cord axis");
            }
    }

    @Test
    void heldPlugToAReelOnTheGroundHangsDownNotUpNorUnderground() throws IOException {
        Matrix4f handProj = perspective(70), worldProj = perspective(90);
        double ground = 64;
        Vector3d cam = new Vector3d(0.5, ground + 1.62, 0.5);
        for (String model : new String[]{"cord_plug", "cable_lug"})
            for (float yaw : new float[]{0, 75, 180, 250})
                for (float pitch : new float[]{-30, 0, 20, 60}) {
                    Quaternionf rot = new Quaternionf().rotationYXZ((float) Math.PI - yaw * DEG, -pitch * DEG, 0);
                    Matrix4f view = new Matrix4f().rotation(rot.conjugate(new Quaternionf()));
                    boolean cord = model.equals("cord_plug");
                    Matrix4f pose = new Matrix4f(view).invert().mul(firstPerson(model, true, 0, 0));
                    Vector3f eye = HeldEnd.eye(view, pose, HeldEnd.backPx(cord).get(new Vector3f()));
                    Vector3f eyeOut = HeldEnd.eye(view, pose, HeldEnd.outPx(cord).get(new Vector3f()));
                    Vector3d at = HeldEnd.world(cam, view, HeldEnd.reproject(eye, handProj, worldProj));
                    Vector3d out = HeldEnd.world(cam, view, HeldEnd.reproject(eyeOut, handProj, worldProj)).sub(at).normalize();
                    Vector3d reel = new Vector3d(cam.x + 7, ground + 0.5, cam.z - 7.1);
                    Vector3d facing = new Vector3d(cam.x - reel.x, 0, cam.z - reel.z).normalize();
                    if (yaw > 200)
                        facing.negate();
                    List<Vector3d> p = CablePath.points(reel, facing, CablePath.RUN, at, out, CablePath.HELD_RUN,
                            CablePath.sagDepth(reel.distance(at), 1), CablePath.Floor.flat(ground + CablePath.RADIUS));
                    String what = model + " yaw " + yaw + " pitch " + pitch;
                    int n = p.size();
                    Vector3d j = p.get(n - 3);
                    assertTrue(j.distance(at) <= CablePath.HELD_RUN + 1e-6, what + ": short run");
                    boolean rising = false;
                    for (int i = n - 3; i >= 0; i--) {
                        Vector3d v = p.get(i);
                        assertTrue(v.y >= ground + CablePath.RADIUS - 1e-9, what + ": under the ground at " + i + " " + v);
                        assertTrue(v.y <= Math.max(j.y, reel.y) + 1e-9, what + ": above its ends at " + i + " " + v);
                        if (i < n - 3) {
                            double dy = v.y - p.get(i + 1).y;
                            if (dy > 1e-6)
                                rising = true;
                            else if (rising)
                                assertTrue(dy > -1e-6, what + ": up and then down again at " + i);
                        }
                    }
                }
    }

    static Vector3f turned(String model, String ctx, Vector3f dir) throws IOException {
        float[] r = vec(display(model, ctx), "rotation", 0);
        return new Quaternionf().rotationXYZ(r[0] * DEG, r[1] * DEG, r[2] * DEG).transform(dir);
    }

    @Test
    void heldTheWayAHandHoldsThem() throws IOException {
        Vector3f blades = turned("cord_plug", "firstperson_righthand", new Vector3f(0, -1, 0));
        Vector3f cord = turned("cord_plug", "firstperson_righthand", new Vector3f(1, 0, 0));
        assertTrue(blades.z < -0.8f && blades.y > 0, "plug blades point ahead, a little up: " + blades);
        assertTrue(cord.y < -0.8f, "plug cord leaves downwards out of the hand: " + cord);
        Vector3f ring = turned("cable_lug", "firstperson_righthand", new Vector3f(0, 0, -1));
        assertTrue(ring.z < -0.8f && ring.y > 0, "lug ring points ahead, a little up: " + ring);
        assertTrue(turned("cord_plug", "thirdperson_righthand", new Vector3f(0, -1, 0)).y > 0.9f, "third person plug blades ahead");
        assertTrue(turned("cord_plug", "thirdperson_righthand", new Vector3f(1, 0, 0)).z < -0.9f, "third person plug cord down");
        assertTrue(turned("cable_lug", "thirdperson_righthand", new Vector3f(0, 0, -1)).y > 0.9f, "third person lug ring ahead");
        for (String model : new String[]{"cord_plug", "cable_lug"})
            for (String ctx : new String[]{"firstperson", "thirdperson"})
                assertEquals(display(model, ctx + "_righthand"), display(model, ctx + "_lefthand"), model + " " + ctx);
    }

    @Test
    void cordPixelIsTheBackOfTheModel() {
        assertEquals(new Vector3d(12, 4, 8), new Vector3d(HeldEnd.backPx(true)));
        assertEquals(LyingEnd.LUG.backPx(), HeldEnd.backPx(false));
    }
}
