package io.github.sjzeero.voltworks.cord;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class Outlets {
    private Outlets() {}

    private static final int[] SOCKET = {SocketBlock.CORD_POSITIVE};

    public static int[] of(BlockState state) {
        if (state.getBlock() instanceof SocketBlock)
            return SOCKET;
        if (state.getBlock() instanceof PowerStripBlock)
            return PowerStripBlock.OUTLETS;
        return new int[0];
    }

    public static boolean has(BlockState state) {
        return of(state).length > 0;
    }

    public static boolean isContact(BlockState state, int id) {
        if (state.getBlock() instanceof SocketBlock && StripGeometry.isOutlet(id))
            return true;
        int supply = id & ~1;
        for (int s : of(state))
            if (s == supply)
                return true;
        return false;
    }

    public static boolean isStripOutlet(BlockState state, int id) {
        return state.getBlock() instanceof SocketBlock && StripGeometry.isOutlet(id);
    }

    public static boolean isStripInput(BlockState state, int id) {
        if (state.getBlock() instanceof SocketBlock)
            return StripGeometry.isInput(id);
        return state.getBlock() instanceof PowerStripBlock && (id == PowerStripBlock.INPUT || id == PowerStripBlock.INPUT + 1);
    }

    public static InWorldNode @Nullable [] pair(Level level, BlockPos pos, int id) {
        BlockState state = level.getBlockState(pos);
        if (!isContact(state, id) && !isStripInput(state, id))
            return null;
        int supply = id & ~1;
        return new InWorldNode[]{new InWorldNode(supply, pos), new InWorldNode(supply + 1, pos)};
    }

    public static int pick(Level level, BlockPos pos, @Nullable Vec3 hit) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SocketBlock)
            return SocketBlock.CORD_POSITIVE;
        return -1;
    }

    public static Vec3 local(Level level, BlockPos pos, Vec3 hit) {
        if (hit.distanceToSqr(Vec3.atCenterOf(pos)) <= 4)
            return hit;
        var sub = WallSpace.subLevel(level, pos);
        return sub == null ? hit : WallSpace.toLocal(sub.logicalPose(), hit);
    }

    public static Vec3 cordDir(Level level, BlockPos pos, int supply) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SocketBlock && StripGeometry.isStripNode(supply)) {
            StripLinks.Placement p = StripLinks.get(level, pos);
            if (p != null)
                return p.dir(supply);
        }
        return cordDir(state, supply);
    }

    public static Vec3 cordDir(BlockState state, int supply) {
        if (state.getBlock() instanceof SocketBlock) {
            Vector3f a = SocketBlock.plugAxis(state);
            double s = state.getValue(SocketBlock.PLUG) == SocketBlock.Plug.B ? -1 : 1;
            return new Vec3(a.x * s, a.y * s, a.z * s);
        }
        return new Vec3(0, 1, 0);
    }

    public static @Nullable Matrix4f plugPose(Level level, BlockPos pos, BlockState state, int supply) {
        if (state.getBlock() instanceof SocketBlock && StripGeometry.isOutlet(supply)) {
            StripLinks.Placement p = StripLinks.get(level, pos);
            if (p == null)
                return null;
            return StripGeometry.plugPose(new Vector3d(p.at().x, p.at().y, p.at().z), p.yaw(),
                    new Vector3d(pos.getX(), pos.getY(), pos.getZ()), supply & ~1);
        }
        return plugPose(state, supply);
    }

    public static @Nullable Matrix4f plugPose(BlockState state, int supply) {
        if (!(state.getBlock() instanceof SocketBlock) || supply != SocketBlock.CORD_POSITIVE)
            return null;
        Matrix4f m = new Matrix4f().translate(0.5f, 0.5f, 0.5f);
        if (state.getValue(SocketBlock.PLUG) == SocketBlock.Plug.NONE)
            state = state.setValue(SocketBlock.PLUG, SocketBlock.Plug.A);
        m.rotate(SocketBlock.modelRotation(state));
        return m.translate(-0.5f, -0.5f, -0.5f);
    }
}
