package io.github.sjzeero.voltworks.reel.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.sjzeero.voltworks.reel.LyingEnd;
import io.github.sjzeero.voltworks.reel.ReelAssetsTest;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The held cable end: the wire must end where the model's cord pixel is drawn. The first-person pose is built the way
 * vanilla's ItemInHandRenderer + ItemTransform.apply + ItemRenderer.render build it, with the real display transforms
 * from the model JSON.
 */
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

    /** ItemTransform.apply: translate (px / 16, x mirrored in the left hand), rotate XYZ (y, z mirrored), scale. */
    static void applyDisplay(Matrix4f m, JsonObject d, boolean leftHand) {
        float[] r = vec(d, "rotation", 0), t = vec(d, "translation", 0), s = vec(d, "scale", 1);
        int i = leftHand ? -1 : 1;
        m.translate(i * t[0] / 16f, t[1] / 16f, t[2] / 16f);
        m.rotate(new Quaternionf().rotationXYZ(r[0] * DEG, i * r[1] * DEG, i * r[2] * DEG));
        m.scale(s[0], s[1], s[2]);
    }

    /** ItemInHandRenderer for a plain item (swing + applyItemArmTransform + applyItemArmAttackTransform), then the model. */
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
        // camera looking somewhere odd: the world modelview is its inverse rotation
        Quaternionf camRot = new Quaternionf().rotationYXZ((float) Math.PI - 37 * DEG, 21 * DEG, 0);
        Matrix4f view = new Matrix4f().rotation(camRot.conjugate(new Quaternionf()));
        Vector3d cam = new Vector3d(100.5, 64.62, -20.25);
        for (String model : new String[]{"cord_plug", "cable_lug"})
            for (boolean right : new boolean[]{true, false})
                for (float swing : new float[]{0, 0.3f, 0.8f}) {
                    Vector3f px = HeldEnd.backPx(model.equals("cord_plug")).get(new Vector3f());
                    // first person: the hand pose has the camera rotation undone and the modelview applies it again
                    Matrix4f pose = new Matrix4f(view).invert().mul(firstPerson(model, right, 0, swing));
                    Vector3f eye = HeldEnd.eye(view, pose, px);
                    Vector3f expect = HeldEnd.ndc(handProj, eye);
                    Vector3d w = HeldEnd.world(cam, view, HeldEnd.reproject(eye, handProj, worldProj));
                    // the wire vertex drawn at w by the world pass
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

    /** Screen direction (ndc x, y) from a to b. */
    static org.joml.Vector2f screen(Matrix4f proj, Vector3f a, Vector3f b) {
        Vector3f na = HeldEnd.ndc(proj, a), nb = HeldEnd.ndc(proj, b);
        return new org.joml.Vector2f(nb.x - na.x, nb.y - na.y).normalize();
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
                // as HeldEndTracker.world does
                Vector3d at = HeldEnd.world(cam, view, HeldEnd.reproject(eye, handProj, worldProj));
                Vector3d out = HeldEnd.world(cam, view, HeldEnd.reproject(eyeOut, handProj, worldProj)).sub(at).normalize();
                Vector3d reel = new Vector3d(cam).add(4, -1.5, 3);
                java.util.List<Vector3d> p = io.github.sjzeero.voltworks.reel.CablePath.points(reel, at, out,
                        io.github.sjzeero.voltworks.reel.CablePath.sagDepth(reel.distance(at), 1), Double.NEGATIVE_INFINITY);
                Vector3d next = p.get(p.size() - 2); // the first wire point out of the model
                Vector3f a = view.transformPosition(new Vector3f((float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z)), new Vector3f());
                Vector3f b = view.transformPosition(new Vector3f((float) (next.x - cam.x), (float) (next.y - cam.y), (float) (next.z - cam.z)), new Vector3f());
                // on screen the first segment continues the model's own cord axis as the hand pass draws it
                org.joml.Vector2f wire = screen(worldProj, a, b), axis = screen(handProj, eye, eyeOut);
                double deg = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, wire.dot(axis)))));
                assertTrue(deg < 3, model + (right ? " right" : " left") + ": first segment " + deg + " degrees off the cord axis");
            }
    }

    /** Model direction {@code dir} after a display context's rotation. */
    static Vector3f turned(String model, String ctx, Vector3f dir) throws IOException {
        float[] r = vec(display(model, ctx), "rotation", 0);
        return new Quaternionf().rotationXYZ(r[0] * DEG, r[1] * DEG, r[2] * DEG).transform(dir);
    }

    @Test
    void heldTheWayAHandHoldsThem() throws IOException {
        // first person item space is view space: -z ahead, +y up
        Vector3f blades = turned("cord_plug", "firstperson_righthand", new Vector3f(0, -1, 0));
        Vector3f cord = turned("cord_plug", "firstperson_righthand", new Vector3f(1, 0, 0));
        assertTrue(blades.z < -0.8f && blades.y > 0, "plug blades point ahead, a little up: " + blades);
        assertTrue(cord.y < -0.8f, "plug cord leaves downwards out of the hand: " + cord);
        Vector3f ring = turned("cable_lug", "firstperson_righthand", new Vector3f(0, 0, -1));
        assertTrue(ring.z < -0.8f && ring.y > 0, "lug ring points ahead, a little up: " + ring);
        // third person (arm hanging): +y is ahead of the player, +z up
        assertTrue(turned("cord_plug", "thirdperson_righthand", new Vector3f(0, -1, 0)).y > 0.9f, "third person plug blades ahead");
        assertTrue(turned("cord_plug", "thirdperson_righthand", new Vector3f(1, 0, 0)).z < -0.9f, "third person plug cord down");
        assertTrue(turned("cable_lug", "thirdperson_righthand", new Vector3f(0, 0, -1)).y > 0.9f, "third person lug ring ahead");
        // the game mirrors the left hand itself, so the left entries must equal the right ones
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
