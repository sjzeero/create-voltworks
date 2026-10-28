package io.github.sjzeero.voltworks.cord;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class StripLinks {
    private StripLinks() {}

    public record Placement(Vec3 at, double yaw) {
        public Vec3 node(int id) {
            Vector3d v = StripGeometry.world(new Vector3d(at.x, at.y, at.z), yaw, StripGeometry.node(id));
            return new Vec3(v.x, v.y, v.z);
        }

        public Vec3 dir(int id) {
            Vector3d v = StripGeometry.turn(yaw, StripGeometry.dir(id));
            return new Vec3(v.x, v.y, v.z);
        }
    }

    public static volatile float partialTick = 1;

    private static final Map<Level, Map<Long, Supplier<Placement>>> LINKS = Collections.synchronizedMap(new WeakHashMap<>());

    private static Map<Long, Supplier<Placement>> of(Level level) {
        return LINKS.computeIfAbsent(level, k -> new ConcurrentHashMap<>());
    }

    static void touch(Level level) {
        of(level);
    }

    public static void put(Level level, BlockPos socket, Supplier<Placement> placement) {
        of(level).put(socket.asLong(), placement);
    }

    public static void remove(Level level, BlockPos socket) {
        of(level).remove(socket.asLong());
    }

    public static void remove(Level level, BlockPos socket, Supplier<Placement> placement) {
        of(level).remove(socket.asLong(), placement);
    }

    public static @Nullable Placement get(Level level, BlockPos socket) {
        if (level instanceof ServerLevel sl && !LINKS.containsKey(sl) && sl.getServer().isSameThread())
            LooseCords.get(sl);
        Supplier<Placement> s = of(level).get(socket.asLong());
        return s == null ? null : s.get();
    }

    public static boolean linked(Level level, BlockPos socket) {
        return get(level, socket) != null;
    }
}
