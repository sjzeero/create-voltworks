package io.github.sjzeero.voltworks.wallwire;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Wall wires are stored in block space: for blocks of a Sable sub-level that is the plot the blocks really sit in,
 * far away from where the sub-level is shown. Anything that compares a wire with the player, the camera or the
 * world has to go through here first.
 */
public final class WallSpace {
    private WallSpace() {
    }

    public static @Nullable SubLevelAccess subLevel(Level level, BlockPos pos) {
        return SableCompanion.INSTANCE.getContaining(level, pos);
    }

    /** True when both blocks are in the main world, or in the same sub-level. */
    public static boolean sameSpace(Level level, BlockPos a, BlockPos b) {
        SubLevelAccess sa = subLevel(level, a), sb = subLevel(level, b);
        return sa == null ? sb == null : sb != null && sa.getUniqueId().equals(sb.getUniqueId());
    }

    public static Vec3 toWorld(Level level, Vec3 pos) {
        return SableCompanion.INSTANCE.projectOutOfSubLevel(level, pos);
    }

    public static double distance(Level level, Vec3 a, Vec3 b) {
        return Math.sqrt(SableCompanion.INSTANCE.distanceSquaredWithSubLevels(level, a, b));
    }

    /** Where the node sits in block space, next to the cells of the wires that start or end on it. */
    public static Vec3 nodePos(Level level, InWorldNode node) {
        Vec3 local = node.getLocalPosition(level);
        return local == null ? node.sourcePos().getCenter() : node.toGlobalPosNoSable(local, level);
    }

    /** Pose used to draw a sub-level at this frame, or null for the main world. */
    public static @Nullable Pose3dc renderPose(Level level, BlockPos pos, float partialTick) {
        SubLevelAccess sub = subLevel(level, pos);
        if(sub instanceof ClientSubLevelAccess client)
            return client.renderPose(partialTick);
        return sub == null ? null : sub.logicalPose();
    }

    public static Vec3 toWorld(@Nullable Pose3dc pose, Vec3 pos) {
        return pose == null ? pos : pose.transformPosition(pos);
    }

    public static Vec3 toLocal(@Nullable Pose3dc pose, Vec3 pos) {
        return pose == null ? pos : pose.transformPositionInverse(pos);
    }

    public static AABB toWorld(@Nullable Pose3dc pose, AABB box) {
        if(pose == null)
            return box;
        AABB out = null;
        for(int i = 0; i < 8; i++) {
            Vec3 c = pose.transformPosition(new Vec3((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ));
            AABB p = new AABB(c, c);
            out = out == null ? p : out.minmax(p);
        }
        return out;
    }
}
