package io.github.sjzeero.voltworks.compat;

import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionHandler;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Bridges CEE API differences between 1.1.1, 1.1.2 and 1.2.0-123/127/132.
 * WireInteractionHandler.targetedPos is a Vec3 in 1.1.x and an OutlinesOnWireRenderer.PosOnWire in 1.2.0.
 */
public final class CeeCompat {
    private static final Logger LOG = LoggerFactory.getLogger("voltworks/cee-compat");
    private static boolean resolved, broken;
    private static Field targetedPos;
    private static boolean posIsVec3;
    private static Method setPos;
    private static Constructor<?> posCtor;

    private static void resolve() {
        resolved = true;
        try {
            targetedPos = WireInteractionHandler.class.getField("targetedPos");
            Class<?> type = targetedPos.getType();
            posIsVec3 = type == Vec3.class;
            if (!posIsVec3) {
                setPos = type.getMethod("setPos", Vec3.class);
                posCtor = type.getConstructor(Vec3.class);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            LOG.warn("Unsupported CEE WireInteractionHandler.targetedPos layout; wall wire hit point will not be highlighted", e);
        }
    }

    public static void setTargetedPos(Vec3 pos) {
        if (!resolved) resolve();
        if (broken) return;
        try {
            if (posIsVec3) { targetedPos.set(null, pos); return; }
            Object cur = targetedPos.get(null);
            if (pos == null) return;
            if (cur == null) targetedPos.set(null, posCtor.newInstance(pos));
            else setPos.invoke(cur, pos);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            LOG.warn("Failed to set CEE targetedPos; disabling", e);
        }
    }

    private CeeCompat() {}
}
