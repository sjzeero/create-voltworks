package io.github.sjzeero.voltworks.solar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import io.github.sjzeero.voltworks.Voltworks;

/**
 * Ported from Create: Power Grid (patryk3211) to Voltworks by sjzeero; electrical side reimplemented on CEE devices.
 * Irradiance, sun occlusion and the panel I-V curve (PG SolarHelper / SolarPanelBlockEntity).
 */
public final class SolarPhysics {
    public static final double SOLAR_CONSTANT = 1361;
    public static final double IDEALITY = 1.5, W_REF = 1000.0, T_REF = 25.0;
    public static final double ALPHA_ISC = 0.0005, BETA_VOC = -0.0035, RS = 0.05;
    public static final double DIFFUSE_FRAC = .12, ALBEDO_FRAC = .08;
    public static final double VOC = 26.4, ISC = 3.3, VMP = 21.12, IMP = 3.036, CELLS = 48, NOCT = 45;

    public static final TagKey<Block> FULL = tag("solar_full_light"), THREE_QUARTERS = tag("solar_three_quarters_light"),
            HALF = tag("solar_half_light"), QUARTER = tag("solar_quarter_light");

    private static TagKey<Block> tag(String n) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("voltworks", n));
    }

    public static double airMass(Level level) {
        double s = Math.max(0, Math.cos(level.getSunAngle(0)));
        if (s <= 0) return Double.POSITIVE_INFINITY;
        double elevDeg = Math.toDegrees(Math.asin(s));
        return Math.max(1, 1.0 / (s + 0.50572 * Math.pow(elevDeg + 6.07995, -1.6364)));
    }

    public static float cloudCover(Level level) {
        if (level.isRaining() && level.isThundering()) return .925f;
        return level.isRaining() ? .85f : 0;
    }

    public static double cellTemp(double irradiance, double ambient) {
        return ambient + (NOCT - 20) * (irradiance / 800);
    }

    /** Ambient temperature in C derived from the biome base temperature (plains 0.8 -> 22 C). */
    public static double ambientTemp(Level level, BlockPos pos) {
        float t = level.getBiome(pos).value().getBaseTemperature();
        return Mth.clamp(22 + (t - 0.8) * 25, -20, 50);
    }

    /** Light transmission factor of a block on the ray, or -1 if it is opaque. */
    private static float transmission(BlockState s) {
        if (s.is(QUARTER)) return .25f;
        if (s.is(HALF)) return .5f;
        if (s.is(THREE_QUARTERS)) return .75f;
        if (s.is(FULL)) return 1f;
        return -1;
    }

    private static boolean blocksRay(Level level, BlockPos p, BlockState s, Vec3 a, Vec3 b) {
        if (s.isAir()) return false;
        if (!s.isCollisionShapeFullBlock(level, p) && s.getBlock() != Blocks.WATER) {
            var shape = s.getShape(level, p);
            return !shape.isEmpty() && shape.clip(a, b, p) != null;
        }
        return true;
    }

    /** Voxel walk from start to end; returns the fraction of light that gets through (0..1). */
    public static float trace(Level level, Vec3 start, Vec3 end, BlockPos self) {
        Vec3 dir = end.subtract(start);
        double length = dir.length();
        if (length < 1e-6) return 1;
        Vec3 n = dir.normalize();
        int x = Mth.floor(start.x), y = Mth.floor(start.y), z = Mth.floor(start.z);
        int ex = Mth.floor(end.x), ey = Mth.floor(end.y), ez = Mth.floor(end.z);
        int sx = n.x >= 0 ? 1 : -1, sy = n.y >= 0 ? 1 : -1, sz = n.z >= 0 ? 1 : -1;
        double dx = n.x == 0 ? Double.MAX_VALUE : Math.abs(1 / n.x), dy = n.y == 0 ? Double.MAX_VALUE : Math.abs(1 / n.y), dz = n.z == 0 ? Double.MAX_VALUE : Math.abs(1 / n.z);
        double tx = n.x == 0 ? Double.MAX_VALUE : (sx > 0 ? Math.ceil(start.x) - start.x : start.x - Math.floor(start.x)) / Math.abs(n.x);
        double ty = n.y == 0 ? Double.MAX_VALUE : (sy > 0 ? Math.ceil(start.y) - start.y : start.y - Math.floor(start.y)) / Math.abs(n.y);
        double tz = n.z == 0 ? Double.MAX_VALUE : (sz > 0 ? Math.ceil(start.z) - start.z : start.z - Math.floor(start.z)) / Math.abs(n.z);
        float light = 1;
        int maxY = level.getMaxBuildHeight();
        for (int i = 0; i < length * 3 && y < maxY; i++) {
            BlockPos p = new BlockPos(x, y, z);
            if (!p.equals(self) && level.isLoaded(p)) {
                BlockState s = level.getBlockState(p);
                if (blocksRay(level, p, s, start, end)) {
                    if (s.getBlock() instanceof SolarPanelBlock) return 0;
                    float t = transmission(s);
                    if (t < 0) return 0;
                    light *= t;
                }
            }
            if (x == ex && y == ey && z == ez) break;
            if (tx < ty && tx < tz) { x += sx; tx += dx; }
            else if (ty < tz) { y += sy; ty += dy; }
            else { z += sz; tz += dz; }
        }
        return light;
    }

    public static float sunVisibility(Level level, BlockPos pos) {
        double a = level.getSunAngle(0);
        Vec3 c = pos.getCenter();
        return trace(level, c, c.add(new Vec3(-Math.sin(a), Math.cos(a), 0).scale(160)), pos);
    }

    public static boolean skyVisible(Level level, BlockPos pos) {
        if (level.canSeeSky(pos)) return true;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        return trace(level, pos.getCenter(), pos.getCenter().add(0, top - pos.getY() + 1, 0), pos) > 0;
    }

    /** Linearised panel I-V curve around v0: returns {Ieq, G} of the Norton equivalent. */
    public static double[] ivCurve(double irradiance, double cellTempC, double v0) {
        return ivCurve(irradiance, cellTempC, v0, 1, 1);
    }

    /** Array of {@code series} panels per string and {@code parallel} strings (PG SolarHelper.IVCurve). */
    public static double[] ivCurve(double irradiance, double cellTempC, double v0, int series, int parallel) {
        double k = 1.380649e-23, q = 1.602176634e-19;
        double dT = cellTempC - T_REF;
        double g = Math.max(0, irradiance) / W_REF;
        if (g <= 1e-6) return new double[]{0, 2e-2};
        double isc = g * (ISC + ALPHA_ISC * ISC * dT) * parallel;
        double logG = Math.log(Math.max(g, 1e-6));
        double delta = IDEALITY * CELLS * (k * (cellTempC + 273.15) / q);
        double voc = (VOC + BETA_VOC * VOC * dT + delta * logG) * series;
        double imp = g * (IMP + ALPHA_ISC * IMP * dT) * parallel;
        double vmp = (VMP + BETA_VOC * VMP * dT + delta * logG) * series;
        if (voc <= 0 || isc <= 0) return new double[]{0, 2e-2};
        double ratio = Math.min(0.999, imp / isc);
        double c2 = (vmp / voc - 1.0) / Math.log(1.0 - ratio);
        double c1 = (1.0 - ratio) * Math.exp(-vmp / (c2 * voc));
        double v = Double.isFinite(v0) ? Math.max(0, Math.min(v0, voc * 1.05)) : 0;
        double e = Math.exp(v / (c2 * voc));
        double i = isc * (1 - c1 * (e - 1));
        double gd = Math.max(Math.max(1e-6, isc * c1 / (c2 * voc) * e), isc / voc);
        return new double[]{i + gd * v, gd};
    }

    private SolarPhysics() {}
}
