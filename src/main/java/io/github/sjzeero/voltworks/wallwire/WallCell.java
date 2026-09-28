package io.github.sjzeero.voltworks.wallwire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One pixel of a block face that a wall wire can lie on. {@code px, py, pz} is the pixel inside {@code pos};
 * the coordinate along the face normal is always the layer touching that face (15 for positive faces, 0 for negative).
 */
public record WallCell(BlockPos pos, Direction face, int px, int py, int pz) {
    public static final double LIFT = 0.01;

    public WallCell {
        pos = pos.immutable();
    }

    public static WallCell of(BlockPos pos, Direction face, int[] p) {
        int[] q = p.clone();
        q[face.getAxis().ordinal()] = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 15 : 0;
        return new WallCell(pos, face, q[0], q[1], q[2]);
    }

    public static WallCell fromHit(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        int[] p = {pixel(local.x), pixel(local.y), pixel(local.z)};
        return of(pos, hit.getDirection(), p);
    }

    private static int pixel(double v) {
        return Mth.clamp(Mth.floor(v * 16), 0, 15);
    }

    public int[] pixels() {
        return new int[]{px, py, pz};
    }

    /** Point on the face at the pixel centre, lifted a little off the surface. */
    public Vec3 point(double lift) {
        double[] c = {(px + 0.5) / 16, (py + 0.5) / 16, (pz + 0.5) / 16};
        int a = face.getAxis().ordinal();
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        c[a] = (positive ? 1 : 0) + (positive ? lift : -lift);
        return new Vec3(pos.getX() + c[0], pos.getY() + c[1], pos.getZ() + c[2]);
    }

    public Vec3 point() {
        return point(LIFT);
    }

    /** The block the wire actually sits in. */
    public BlockPos front() {
        return pos.relative(face);
    }

    public int packLocal() {
        return face.get3DDataValue() << 12 | px << 8 | py << 4 | pz;
    }

    public static WallCell unpack(long pos, int local) {
        return new WallCell(BlockPos.of(pos), Direction.from3DDataValue(local >> 12 & 7), local >> 8 & 15, local >> 4 & 15, local & 15);
    }

    /** Manhattan distance in pixels between the unlifted points of two cells. */
    public int manhattan(WallCell o) {
        Vec3 a = point(0), b = o.point(0);
        return (int) Math.round((Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z)) * 16);
    }
}
