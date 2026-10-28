package io.github.sjzeero.voltworks.wallwire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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

    public static WallCell ofPixel(int x, int y, int z, Direction face) {
        return new WallCell(new BlockPos(x >> 4, y >> 4, z >> 4), face, x & 15, y & 15, z & 15);
    }

    public static WallCell fromHit(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        int[] p = {pixel(local.x), pixel(local.y), pixel(local.z)};
        int a = face.getAxis().ordinal();
        int layer = (int) Math.round(face.getAxis().choose(local.x, local.y, local.z) * 16);
        p[a] = Mth.clamp(face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? layer - 1 : layer, 0, 15);
        return new WallCell(pos, face, p[0], p[1], p[2]);
    }

    static int pixel(double v) {
        return Mth.clamp(Mth.floor(v * 16), 0, 15);
    }

    public int[] worldPixel() {
        return new int[]{pos.getX() * 16 + px, pos.getY() * 16 + py, pos.getZ() * 16 + pz};
    }

    public int[] pixels() {
        return new int[]{px, py, pz};
    }

    public Vec3 point(double lift) {
        double[] c = {(px + 0.5) / 16, (py + 0.5) / 16, (pz + 0.5) / 16};
        int a = face.getAxis().ordinal();
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        int layer = pixels()[a];
        c[a] = positive ? (layer + 1) / 16.0 + lift : layer / 16.0 - lift;
        return new Vec3(pos.getX() + c[0], pos.getY() + c[1], pos.getZ() + c[2]);
    }

    public Vec3 point() {
        return point(LIFT);
    }

    public boolean onBlockFace() {
        return pixels()[face.getAxis().ordinal()] == (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 15 : 0);
    }

    public BlockPos front() {
        return onBlockFace() ? pos.relative(face) : pos;
    }

    public int packLocal() {
        return face.get3DDataValue() << 12 | px << 8 | py << 4 | pz;
    }

    public static WallCell unpack(long pos, int local) {
        return new WallCell(BlockPos.of(pos), Direction.from3DDataValue(local >> 12 & 7), local >> 8 & 15, local >> 4 & 15, local & 15);
    }

    public int manhattan(WallCell o) {
        Vec3 a = point(0), b = o.point(0);
        return (int) Math.round((Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z)) * 16);
    }
}
